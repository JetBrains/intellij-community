# bugzilla.py - bugzilla integration for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for integrating with the Bugzilla bug tracker

This hook extension adds comments on bugs in Bugzilla when changesets
that refer to bugs by Bugzilla ID are seen. The hook does not change
bug status.

The hook updates the Bugzilla database directly. Only Bugzilla
installations using MySQL are supported.

The hook relies on a Bugzilla script to send bug change notification
emails. That script changes between Bugzilla versions; the
'processmail' script used prior to 2.18 is replaced in 2.18 and
subsequent versions by 'config/sendbugmail.pl'. Note that these will
be run by Mercurial as the user pushing the change; you will need to
ensure the Bugzilla install file permissions are set appropriately.

The extension is configured through three different configuration
sections. These keys are recognized in the [bugzilla] section:

host
  Hostname of the MySQL server holding the Bugzilla database.

db
  Name of the Bugzilla database in MySQL. Default 'bugs'.

user
  Username to use to access MySQL server. Default 'bugs'.

password
  Password to use to access MySQL server.

timeout
  Database connection timeout (seconds). Default 5.

version
  Bugzilla version. Specify '3.0' for Bugzilla versions 3.0 and later,
  '2.18' for Bugzilla versions from 2.18 and '2.16' for versions prior
  to 2.18.

bzuser
  Fallback Bugzilla user name to record comments with, if changeset
  committer cannot be found as a Bugzilla user.

bzdir
   Bugzilla install directory. Used by default notify. Default
   '/var/www/html/bugzilla'.

notify
  The command to run to get Bugzilla to send bug change notification
  emails. Substitutes from a map with 3 keys, 'bzdir', 'id' (bug id)
  and 'user' (committer bugzilla email). Default depends on version;
  from 2.18 it is "cd %(bzdir)s && perl -T contrib/sendbugmail.pl
  %(id)s %(user)s".

regexp
  Regular expression to match bug IDs in changeset commit message.
  Must contain one "()" group. The default expression matches 'Bug
  1234', 'Bug no. 1234', 'Bug number 1234', 'Bugs 1234,5678', 'Bug
  1234 and 5678' and variations thereof. Matching is case insensitive.

style
  The style file to use when formatting comments.

template
  Template to use when formatting comments. Overrides style if
  specified. In addition to the usual Mercurial keywords, the
  extension specifies::

    {bug}       The Bugzilla bug ID.
    {root}      The full pathname of the Mercurial repository.
    {webroot}   Stripped pathname of the Mercurial repository.
    {hgweb}     Base URL for browsing Mercurial repositories.

  Default 'changeset {node|short} in repo {root} refers '
          'to bug {bug}.\\ndetails:\\n\\t{desc|tabindent}'

strip
  The number of slashes to strip from the front of {root} to produce
  {webroot}. Default 0.

usermap
  Path of file containing Mercurial committer ID to Bugzilla user ID
  mappings. If specified, the file should contain one mapping per
  line, "committer"="Bugzilla user". See also the [usermap] section.

The [usermap] section is used to specify mappings of Mercurial
committer ID to Bugzilla user ID. See also [bugzilla].usermap.
"committer"="Bugzilla user"

Finally, the [web] section supports one entry:

baseurl
  Base URL for browsing Mercurial repositories. Reference from
  templates as {hgweb}.

Activating the extension::

    [extensions]
    bugzilla =

    [hooks]
    # run bugzilla hook on every change pulled or pushed in here
    incoming.bugzilla = python:hgext.bugzilla.hook

Example configuration:

This example configuration is for a collection of Mercurial
repositories in /var/local/hg/repos/ used with a local Bugzilla 3.2
installation in /opt/bugzilla-3.2. ::

    [bugzilla]
    host=localhost
    password=XYZZY
    version=3.0
    bzuser=unknown@domain.com
    bzdir=/opt/bugzilla-3.2
    template=Changeset {node|short} in {root|basename}.
             {hgweb}/{webroot}/rev/{node|short}\\n
             {desc}\\n
    strip=5

    [web]
    baseurl=http://dev.domain.com/hg

    [usermap]
    user@emaildomain.com=user.name@bugzilladomain.com

Commits add a comment to the Bugzilla bug record of the form::

    Changeset 3b16791d6642 in repository-name.
    http://dev.domain.com/hg/repository-name/rev/3b16791d6642

    Changeset commit comment. Bug 1234.
'''

from mercurial.i18n import _
from mercurial.node import short
from mercurial import cmdutil, templater, util
import re, time

MySQLdb = None

def buglist(ids):
    return '(' + ','.join(map(str, ids)) + ')'

class bugzilla_2_16(object):
    '''support for bugzilla version 2.16.'''

    def __init__(self, ui):
        self.ui = ui
        host = self.ui.config('bugzilla', 'host', 'localhost')
        user = self.ui.config('bugzilla', 'user', 'bugs')
        passwd = self.ui.config('bugzilla', 'password')
        db = self.ui.config('bugzilla', 'db', 'bugs')
        timeout = int(self.ui.config('bugzilla', 'timeout', 5))
        usermap = self.ui.config('bugzilla', 'usermap')
        if usermap:
            self.ui.readconfig(usermap, sections=['usermap'])
        self.ui.note(_('connecting to %s:%s as %s, password %s\n') %
                     (host, db, user, '*' * len(passwd)))
        self.conn = MySQLdb.connect(host=host, user=user, passwd=passwd,
                                    db=db, connect_timeout=timeout)
        self.cursor = self.conn.cursor()
        self.longdesc_id = self.get_longdesc_id()
        self.user_ids = {}
        self.default_notify = "cd %(bzdir)s && ./processmail %(id)s %(user)s"

    def run(self, *args, **kwargs):
        '''run a query.'''
        self.ui.note(_('query: %s %s\n') % (args, kwargs))
        try:
            self.cursor.execute(*args, **kwargs)
        except MySQLdb.MySQLError:
            self.ui.note(_('failed query: %s %s\n') % (args, kwargs))
            raise

    def get_longdesc_id(self):
        '''get identity of longdesc field'''
        self.run('select fieldid from fielddefs where name = "longdesc"')
        ids = self.cursor.fetchall()
        if len(ids) != 1:
            raise util.Abort(_('unknown database schema'))
        return ids[0][0]

    def filter_real_bug_ids(self, ids):
        '''filter not-existing bug ids from list.'''
        self.run('select bug_id from bugs where bug_id in %s' % buglist(ids))
        return sorted([c[0] for c in self.cursor.fetchall()])

    def filter_unknown_bug_ids(self, node, ids):
        '''filter bug ids from list that already refer to this changeset.'''

        self.run('''select bug_id from longdescs where
                    bug_id in %s and thetext like "%%%s%%"''' %
                 (buglist(ids), short(node)))
        unknown = set(ids)
        for (id,) in self.cursor.fetchall():
            self.ui.status(_('bug %d already knows about changeset %s\n') %
                           (id, short(node)))
            unknown.discard(id)
        return sorted(unknown)

    def notify(self, ids, committer):
        '''tell bugzilla to send mail.'''

        self.ui.status(_('telling bugzilla to send mail:\n'))
        (user, userid) = self.get_bugzilla_user(committer)
        for id in ids:
            self.ui.status(_('  bug %s\n') % id)
            cmdfmt = self.ui.config('bugzilla', 'notify', self.default_notify)
            bzdir = self.ui.config('bugzilla', 'bzdir', '/var/www/html/bugzilla')
            try:
                # Backwards-compatible with old notify string, which
                # took one string. This will throw with a new format
                # string.
                cmd = cmdfmt % id
            except TypeError:
                cmd = cmdfmt % {'bzdir': bzdir, 'id': id, 'user': user}
            self.ui.note(_('running notify command %s\n') % cmd)
            fp = util.popen('(%s) 2>&1' % cmd)
            out = fp.read()
            ret = fp.close()
            if ret:
                self.ui.warn(out)
                raise util.Abort(_('bugzilla notify command %s') %
                                 util.explain_exit(ret)[0])
        self.ui.status(_('done\n'))

    def get_user_id(self, user):
        '''look up numeric bugzilla user id.'''
        try:
            return self.user_ids[user]
        except KeyError:
            try:
                userid = int(user)
            except ValueError:
                self.ui.note(_('looking up user %s\n') % user)
                self.run('''select userid from profiles
                            where login_name like %s''', user)
                all = self.cursor.fetchall()
                if len(all) != 1:
                    raise KeyError(user)
                userid = int(all[0][0])
            self.user_ids[user] = userid
            return userid

    def map_committer(self, user):
        '''map name of committer to bugzilla user name.'''
        for committer, bzuser in self.ui.configitems('usermap'):
            if committer.lower() == user.lower():
                return bzuser
        return user

    def get_bugzilla_user(self, committer):
        '''see if committer is a registered bugzilla user. Return
        bugzilla username and userid if so. If not, return default
        bugzilla username and userid.'''
        user = self.map_committer(committer)
        try:
            userid = self.get_user_id(user)
        except KeyError:
            try:
                defaultuser = self.ui.config('bugzilla', 'bzuser')
                if not defaultuser:
                    raise util.Abort(_('cannot find bugzilla user id for %s') %
                                     user)
                userid = self.get_user_id(defaultuser)
                user = defaultuser
            except KeyError:
                raise util.Abort(_('cannot find bugzilla user id for %s or %s') %
                                 (user, defaultuser))
        return (user, userid)

    def add_comment(self, bugid, text, committer):
        '''add comment to bug. try adding comment as committer of
        changeset, otherwise as default bugzilla user.'''
        (user, userid) = self.get_bugzilla_user(committer)
        now = time.strftime('%Y-%m-%d %H:%M:%S')
        self.run('''insert into longdescs
                    (bug_id, who, bug_when, thetext)
                    values (%s, %s, %s, %s)''',
                 (bugid, userid, now, text))
        self.run('''insert into bugs_activity (bug_id, who, bug_when, fieldid)
                    values (%s, %s, %s, %s)''',
                 (bugid, userid, now, self.longdesc_id))
        self.conn.commit()

class bugzilla_2_18(bugzilla_2_16):
    '''support for bugzilla 2.18 series.'''

    def __init__(self, ui):
        bugzilla_2_16.__init__(self, ui)
        self.default_notify = \
            "cd %(bzdir)s && perl -T contrib/sendbugmail.pl %(id)s %(user)s"

class bugzilla_3_0(bugzilla_2_18):
    '''support for bugzilla 3.0 series.'''

    def __init__(self, ui):
        bugzilla_2_18.__init__(self, ui)

    def get_longdesc_id(self):
        '''get identity of longdesc field'''
        self.run('select id from fielddefs where name = "longdesc"')
        ids = self.cursor.fetchall()
        if len(ids) != 1:
            raise util.Abort(_('unknown database schema'))
        return ids[0][0]

class bugzilla(object):
    # supported versions of bugzilla. different versions have
    # different schemas.
    _versions = {
        '2.16': bugzilla_2_16,
        '2.18': bugzilla_2_18,
        '3.0':  bugzilla_3_0
        }

    _default_bug_re = (r'bugs?\s*,?\s*(?:#|nos?\.?|num(?:ber)?s?)?\s*'
                       r'((?:\d+\s*(?:,?\s*(?:and)?)?\s*)+)')

    _bz = None

    def __init__(self, ui, repo):
        self.ui = ui
        self.repo = repo

    def bz(self):
        '''return object that knows how to talk to bugzilla version in
        use.'''

        if bugzilla._bz is None:
            bzversion = self.ui.config('bugzilla', 'version')
            try:
                bzclass = bugzilla._versions[bzversion]
            except KeyError:
                raise util.Abort(_('bugzilla version %s not supported') %
                                 bzversion)
            bugzilla._bz = bzclass(self.ui)
        return bugzilla._bz

    def __getattr__(self, key):
        return getattr(self.bz(), key)

    _bug_re = None
    _split_re = None

    def find_bug_ids(self, ctx):
        '''find valid bug ids that are referred to in changeset
        comments and that do not already have references to this
        changeset.'''

        if bugzilla._bug_re is None:
            bugzilla._bug_re = re.compile(
                self.ui.config('bugzilla', 'regexp', bugzilla._default_bug_re),
                re.IGNORECASE)
            bugzilla._split_re = re.compile(r'\D+')
        start = 0
        ids = set()
        while True:
            m = bugzilla._bug_re.search(ctx.description(), start)
            if not m:
                break
            start = m.end()
            for id in bugzilla._split_re.split(m.group(1)):
                if not id:
                    continue
                ids.add(int(id))
        if ids:
            ids = self.filter_real_bug_ids(ids)
        if ids:
            ids = self.filter_unknown_bug_ids(ctx.node(), ids)
        return ids

    def update(self, bugid, ctx):
        '''update bugzilla bug with reference to changeset.'''

        def webroot(root):
            '''strip leading prefix of repo root and turn into
            url-safe path.'''
            count = int(self.ui.config('bugzilla', 'strip', 0))
            root = util.pconvert(root)
            while count > 0:
                c = root.find('/')
                if c == -1:
                    break
                root = root[c + 1:]
                count -= 1
            return root

        mapfile = self.ui.config('bugzilla', 'style')
        tmpl = self.ui.config('bugzilla', 'template')
        t = cmdutil.changeset_templater(self.ui, self.repo,
                                        False, None, mapfile, False)
        if not mapfile and not tmpl:
            tmpl = _('changeset {node|short} in repo {root} refers '
                     'to bug {bug}.\ndetails:\n\t{desc|tabindent}')
        if tmpl:
            tmpl = templater.parsestring(tmpl, quoted=False)
            t.use_template(tmpl)
        self.ui.pushbuffer()
        t.show(ctx, changes=ctx.changeset(),
               bug=str(bugid),
               hgweb=self.ui.config('web', 'baseurl'),
               root=self.repo.root,
               webroot=webroot(self.repo.root))
        data = self.ui.popbuffer()
        self.add_comment(bugid, data, util.email(ctx.user()))

def hook(ui, repo, hooktype, node=None, **kwargs):
    '''add comment to bugzilla for each changeset that refers to a
    bugzilla bug id. only add a comment once per bug, so same change
    seen multiple times does not fill bug with duplicate data.'''
    try:
        import MySQLdb as mysql
        global MySQLdb
        MySQLdb = mysql
    except ImportError, err:
        raise util.Abort(_('python mysql support not available: %s') % err)

    if node is None:
        raise util.Abort(_('hook type %s does not pass a changeset id') %
                         hooktype)
    try:
        bz = bugzilla(ui, repo)
        ctx = repo[node]
        ids = bz.find_bug_ids(ctx)
        if ids:
            for id in ids:
                bz.update(id, ctx)
            bz.notify(ids, util.email(ctx.user()))
    except MySQLdb.MySQLError, err:
        raise util.Abort(_('database error: %s') % err[1])

