# bugzilla.py - bugzilla integration for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
# Copyright 2011-2 Jim Hague <jim.hague@acm.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for integrating with the Bugzilla bug tracker

This hook extension adds comments on bugs in Bugzilla when changesets
that refer to bugs by Bugzilla ID are seen. The comment is formatted using
the Mercurial template mechanism.

The bug references can optionally include an update for Bugzilla of the
hours spent working on the bug. Bugs can also be marked fixed.

Three basic modes of access to Bugzilla are provided:

1. Access via the Bugzilla XMLRPC interface. Requires Bugzilla 3.4 or later.

2. Check data via the Bugzilla XMLRPC interface and submit bug change
   via email to Bugzilla email interface. Requires Bugzilla 3.4 or later.

3. Writing directly to the Bugzilla database. Only Bugzilla installations
   using MySQL are supported. Requires Python MySQLdb.

Writing directly to the database is susceptible to schema changes, and
relies on a Bugzilla contrib script to send out bug change
notification emails. This script runs as the user running Mercurial,
must be run on the host with the Bugzilla install, and requires
permission to read Bugzilla configuration details and the necessary
MySQL user and password to have full access rights to the Bugzilla
database. For these reasons this access mode is now considered
deprecated, and will not be updated for new Bugzilla versions going
forward. Only adding comments is supported in this access mode.

Access via XMLRPC needs a Bugzilla username and password to be specified
in the configuration. Comments are added under that username. Since the
configuration must be readable by all Mercurial users, it is recommended
that the rights of that user are restricted in Bugzilla to the minimum
necessary to add comments. Marking bugs fixed requires Bugzilla 4.0 and later.

Access via XMLRPC/email uses XMLRPC to query Bugzilla, but sends
email to the Bugzilla email interface to submit comments to bugs.
The From: address in the email is set to the email address of the Mercurial
user, so the comment appears to come from the Mercurial user. In the event
that the Mercurial user email is not recognized by Bugzilla as a Bugzilla
user, the email associated with the Bugzilla username used to log into
Bugzilla is used instead as the source of the comment. Marking bugs fixed
works on all supported Bugzilla versions.

Configuration items common to all access modes:

bugzilla.version
  The access type to use. Values recognized are:

  :``xmlrpc``:       Bugzilla XMLRPC interface.
  :``xmlrpc+email``: Bugzilla XMLRPC and email interfaces.
  :``3.0``:          MySQL access, Bugzilla 3.0 and later.
  :``2.18``:         MySQL access, Bugzilla 2.18 and up to but not
                     including 3.0.
  :``2.16``:         MySQL access, Bugzilla 2.16 and up to but not
                     including 2.18.

bugzilla.regexp
  Regular expression to match bug IDs for update in changeset commit message.
  It must contain one "()" named group ``<ids>`` containing the bug
  IDs separated by non-digit characters. It may also contain
  a named group ``<hours>`` with a floating-point number giving the
  hours worked on the bug. If no named groups are present, the first
  "()" group is assumed to contain the bug IDs, and work time is not
  updated. The default expression matches ``Bug 1234``, ``Bug no. 1234``,
  ``Bug number 1234``, ``Bugs 1234,5678``, ``Bug 1234 and 5678`` and
  variations thereof, followed by an hours number prefixed by ``h`` or
  ``hours``, e.g. ``hours 1.5``. Matching is case insensitive.

bugzilla.fixregexp
  Regular expression to match bug IDs for marking fixed in changeset
  commit message. This must contain a "()" named group ``<ids>` containing
  the bug IDs separated by non-digit characters. It may also contain
  a named group ``<hours>`` with a floating-point number giving the
  hours worked on the bug. If no named groups are present, the first
  "()" group is assumed to contain the bug IDs, and work time is not
  updated. The default expression matches ``Fixes 1234``, ``Fixes bug 1234``,
  ``Fixes bugs 1234,5678``, ``Fixes 1234 and 5678`` and
  variations thereof, followed by an hours number prefixed by ``h`` or
  ``hours``, e.g. ``hours 1.5``. Matching is case insensitive.

bugzilla.fixstatus
  The status to set a bug to when marking fixed. Default ``RESOLVED``.

bugzilla.fixresolution
  The resolution to set a bug to when marking fixed. Default ``FIXED``.

bugzilla.style
  The style file to use when formatting comments.

bugzilla.template
  Template to use when formatting comments. Overrides style if
  specified. In addition to the usual Mercurial keywords, the
  extension specifies:

  :``{bug}``:     The Bugzilla bug ID.
  :``{root}``:    The full pathname of the Mercurial repository.
  :``{webroot}``: Stripped pathname of the Mercurial repository.
  :``{hgweb}``:   Base URL for browsing Mercurial repositories.

  Default ``changeset {node|short} in repo {root} refers to bug
  {bug}.\\ndetails:\\n\\t{desc|tabindent}``

bugzilla.strip
  The number of path separator characters to strip from the front of
  the Mercurial repository path (``{root}`` in templates) to produce
  ``{webroot}``. For example, a repository with ``{root}``
  ``/var/local/my-project`` with a strip of 2 gives a value for
  ``{webroot}`` of ``my-project``. Default 0.

web.baseurl
  Base URL for browsing Mercurial repositories. Referenced from
  templates as ``{hgweb}``.

Configuration items common to XMLRPC+email and MySQL access modes:

bugzilla.usermap
  Path of file containing Mercurial committer email to Bugzilla user email
  mappings. If specified, the file should contain one mapping per
  line::

    committer = Bugzilla user

  See also the ``[usermap]`` section.

The ``[usermap]`` section is used to specify mappings of Mercurial
committer email to Bugzilla user email. See also ``bugzilla.usermap``.
Contains entries of the form ``committer = Bugzilla user``.

XMLRPC access mode configuration:

bugzilla.bzurl
  The base URL for the Bugzilla installation.
  Default ``http://localhost/bugzilla``.

bugzilla.user
  The username to use to log into Bugzilla via XMLRPC. Default
  ``bugs``.

bugzilla.password
  The password for Bugzilla login.

XMLRPC+email access mode uses the XMLRPC access mode configuration items,
and also:

bugzilla.bzemail
  The Bugzilla email address.

In addition, the Mercurial email settings must be configured. See the
documentation in hgrc(5), sections ``[email]`` and ``[smtp]``.

MySQL access mode configuration:

bugzilla.host
  Hostname of the MySQL server holding the Bugzilla database.
  Default ``localhost``.

bugzilla.db
  Name of the Bugzilla database in MySQL. Default ``bugs``.

bugzilla.user
  Username to use to access MySQL server. Default ``bugs``.

bugzilla.password
  Password to use to access MySQL server.

bugzilla.timeout
  Database connection timeout (seconds). Default 5.

bugzilla.bzuser
  Fallback Bugzilla user name to record comments with, if changeset
  committer cannot be found as a Bugzilla user.

bugzilla.bzdir
   Bugzilla install directory. Used by default notify. Default
   ``/var/www/html/bugzilla``.

bugzilla.notify
  The command to run to get Bugzilla to send bug change notification
  emails. Substitutes from a map with 3 keys, ``bzdir``, ``id`` (bug
  id) and ``user`` (committer bugzilla email). Default depends on
  version; from 2.18 it is "cd %(bzdir)s && perl -T
  contrib/sendbugmail.pl %(id)s %(user)s".

Activating the extension::

    [extensions]
    bugzilla =

    [hooks]
    # run bugzilla hook on every change pulled or pushed in here
    incoming.bugzilla = python:hgext.bugzilla.hook

Example configurations:

XMLRPC example configuration. This uses the Bugzilla at
``http://my-project.org/bugzilla``, logging in as user
``bugmail@my-project.org`` with password ``plugh``. It is used with a
collection of Mercurial repositories in ``/var/local/hg/repos/``,
with a web interface at ``http://my-project.org/hg``. ::

    [bugzilla]
    bzurl=http://my-project.org/bugzilla
    user=bugmail@my-project.org
    password=plugh
    version=xmlrpc
    template=Changeset {node|short} in {root|basename}.
             {hgweb}/{webroot}/rev/{node|short}\\n
             {desc}\\n
    strip=5

    [web]
    baseurl=http://my-project.org/hg

XMLRPC+email example configuration. This uses the Bugzilla at
``http://my-project.org/bugzilla``, logging in as user
``bugmail@my-project.org`` with password ``plugh``. It is used with a
collection of Mercurial repositories in ``/var/local/hg/repos/``,
with a web interface at ``http://my-project.org/hg``. Bug comments
are sent to the Bugzilla email address
``bugzilla@my-project.org``. ::

    [bugzilla]
    bzurl=http://my-project.org/bugzilla
    user=bugmail@my-project.org
    password=plugh
    version=xmlrpc
    bzemail=bugzilla@my-project.org
    template=Changeset {node|short} in {root|basename}.
             {hgweb}/{webroot}/rev/{node|short}\\n
             {desc}\\n
    strip=5

    [web]
    baseurl=http://my-project.org/hg

    [usermap]
    user@emaildomain.com=user.name@bugzilladomain.com

MySQL example configuration. This has a local Bugzilla 3.2 installation
in ``/opt/bugzilla-3.2``. The MySQL database is on ``localhost``,
the Bugzilla database name is ``bugs`` and MySQL is
accessed with MySQL username ``bugs`` password ``XYZZY``. It is used
with a collection of Mercurial repositories in ``/var/local/hg/repos/``,
with a web interface at ``http://my-project.org/hg``. ::

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
    baseurl=http://my-project.org/hg

    [usermap]
    user@emaildomain.com=user.name@bugzilladomain.com

All the above add a comment to the Bugzilla bug record of the form::

    Changeset 3b16791d6642 in repository-name.
    http://my-project.org/hg/repository-name/rev/3b16791d6642

    Changeset commit comment. Bug 1234.
'''

from mercurial.i18n import _
from mercurial.node import short
from mercurial import cmdutil, mail, templater, util
import re, time, urlparse, xmlrpclib

testedwith = 'internal'

class bzaccess(object):
    '''Base class for access to Bugzilla.'''

    def __init__(self, ui):
        self.ui = ui
        usermap = self.ui.config('bugzilla', 'usermap')
        if usermap:
            self.ui.readconfig(usermap, sections=['usermap'])

    def map_committer(self, user):
        '''map name of committer to Bugzilla user name.'''
        for committer, bzuser in self.ui.configitems('usermap'):
            if committer.lower() == user.lower():
                return bzuser
        return user

    # Methods to be implemented by access classes.
    #
    # 'bugs' is a dict keyed on bug id, where values are a dict holding
    # updates to bug state. Recognized dict keys are:
    #
    # 'hours': Value, float containing work hours to be updated.
    # 'fix':   If key present, bug is to be marked fixed. Value ignored.

    def filter_real_bug_ids(self, bugs):
        '''remove bug IDs that do not exist in Bugzilla from bugs.'''
        pass

    def filter_cset_known_bug_ids(self, node, bugs):
        '''remove bug IDs where node occurs in comment text from bugs.'''
        pass

    def updatebug(self, bugid, newstate, text, committer):
        '''update the specified bug. Add comment text and set new states.

        If possible add the comment as being from the committer of
        the changeset. Otherwise use the default Bugzilla user.
        '''
        pass

    def notify(self, bugs, committer):
        '''Force sending of Bugzilla notification emails.

        Only required if the access method does not trigger notification
        emails automatically.
        '''
        pass

# Bugzilla via direct access to MySQL database.
class bzmysql(bzaccess):
    '''Support for direct MySQL access to Bugzilla.

    The earliest Bugzilla version this is tested with is version 2.16.

    If your Bugzilla is version 3.4 or above, you are strongly
    recommended to use the XMLRPC access method instead.
    '''

    @staticmethod
    def sql_buglist(ids):
        '''return SQL-friendly list of bug ids'''
        return '(' + ','.join(map(str, ids)) + ')'

    _MySQLdb = None

    def __init__(self, ui):
        try:
            import MySQLdb as mysql
            bzmysql._MySQLdb = mysql
        except ImportError, err:
            raise util.Abort(_('python mysql support not available: %s') % err)

        bzaccess.__init__(self, ui)

        host = self.ui.config('bugzilla', 'host', 'localhost')
        user = self.ui.config('bugzilla', 'user', 'bugs')
        passwd = self.ui.config('bugzilla', 'password')
        db = self.ui.config('bugzilla', 'db', 'bugs')
        timeout = int(self.ui.config('bugzilla', 'timeout', 5))
        self.ui.note(_('connecting to %s:%s as %s, password %s\n') %
                     (host, db, user, '*' * len(passwd)))
        self.conn = bzmysql._MySQLdb.connect(host=host,
                                                   user=user, passwd=passwd,
                                                   db=db,
                                                   connect_timeout=timeout)
        self.cursor = self.conn.cursor()
        self.longdesc_id = self.get_longdesc_id()
        self.user_ids = {}
        self.default_notify = "cd %(bzdir)s && ./processmail %(id)s %(user)s"

    def run(self, *args, **kwargs):
        '''run a query.'''
        self.ui.note(_('query: %s %s\n') % (args, kwargs))
        try:
            self.cursor.execute(*args, **kwargs)
        except bzmysql._MySQLdb.MySQLError:
            self.ui.note(_('failed query: %s %s\n') % (args, kwargs))
            raise

    def get_longdesc_id(self):
        '''get identity of longdesc field'''
        self.run('select fieldid from fielddefs where name = "longdesc"')
        ids = self.cursor.fetchall()
        if len(ids) != 1:
            raise util.Abort(_('unknown database schema'))
        return ids[0][0]

    def filter_real_bug_ids(self, bugs):
        '''filter not-existing bugs from set.'''
        self.run('select bug_id from bugs where bug_id in %s' %
                 bzmysql.sql_buglist(bugs.keys()))
        existing = [id for (id,) in self.cursor.fetchall()]
        for id in bugs.keys():
            if id not in existing:
                self.ui.status(_('bug %d does not exist\n') % id)
                del bugs[id]

    def filter_cset_known_bug_ids(self, node, bugs):
        '''filter bug ids that already refer to this changeset from set.'''
        self.run('''select bug_id from longdescs where
                    bug_id in %s and thetext like "%%%s%%"''' %
                 (bzmysql.sql_buglist(bugs.keys()), short(node)))
        for (id,) in self.cursor.fetchall():
            self.ui.status(_('bug %d already knows about changeset %s\n') %
                           (id, short(node)))
            del bugs[id]

    def notify(self, bugs, committer):
        '''tell bugzilla to send mail.'''
        self.ui.status(_('telling bugzilla to send mail:\n'))
        (user, userid) = self.get_bugzilla_user(committer)
        for id in bugs.keys():
            self.ui.status(_('  bug %s\n') % id)
            cmdfmt = self.ui.config('bugzilla', 'notify', self.default_notify)
            bzdir = self.ui.config('bugzilla', 'bzdir',
                                   '/var/www/html/bugzilla')
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
                                 util.explainexit(ret)[0])
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

    def get_bugzilla_user(self, committer):
        '''See if committer is a registered bugzilla user. Return
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
                raise util.Abort(_('cannot find bugzilla user id for %s or %s')
                                 % (user, defaultuser))
        return (user, userid)

    def updatebug(self, bugid, newstate, text, committer):
        '''update bug state with comment text.

        Try adding comment as committer of changeset, otherwise as
        default bugzilla user.'''
        if len(newstate) > 0:
            self.ui.warn(_("Bugzilla/MySQL cannot update bug state\n"))

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

class bzmysql_2_18(bzmysql):
    '''support for bugzilla 2.18 series.'''

    def __init__(self, ui):
        bzmysql.__init__(self, ui)
        self.default_notify = \
            "cd %(bzdir)s && perl -T contrib/sendbugmail.pl %(id)s %(user)s"

class bzmysql_3_0(bzmysql_2_18):
    '''support for bugzilla 3.0 series.'''

    def __init__(self, ui):
        bzmysql_2_18.__init__(self, ui)

    def get_longdesc_id(self):
        '''get identity of longdesc field'''
        self.run('select id from fielddefs where name = "longdesc"')
        ids = self.cursor.fetchall()
        if len(ids) != 1:
            raise util.Abort(_('unknown database schema'))
        return ids[0][0]

# Bugzilla via XMLRPC interface.

class cookietransportrequest(object):
    """A Transport request method that retains cookies over its lifetime.

    The regular xmlrpclib transports ignore cookies. Which causes
    a bit of a problem when you need a cookie-based login, as with
    the Bugzilla XMLRPC interface.

    So this is a helper for defining a Transport which looks for
    cookies being set in responses and saves them to add to all future
    requests.
    """

    # Inspiration drawn from
    # http://blog.godson.in/2010/09/how-to-make-python-xmlrpclib-client.html
    # http://www.itkovian.net/base/transport-class-for-pythons-xml-rpc-lib/

    cookies = []
    def send_cookies(self, connection):
        if self.cookies:
            for cookie in self.cookies:
                connection.putheader("Cookie", cookie)

    def request(self, host, handler, request_body, verbose=0):
        self.verbose = verbose
        self.accept_gzip_encoding = False

        # issue XML-RPC request
        h = self.make_connection(host)
        if verbose:
            h.set_debuglevel(1)

        self.send_request(h, handler, request_body)
        self.send_host(h, host)
        self.send_cookies(h)
        self.send_user_agent(h)
        self.send_content(h, request_body)

        # Deal with differences between Python 2.4-2.6 and 2.7.
        # In the former h is a HTTP(S). In the latter it's a
        # HTTP(S)Connection. Luckily, the 2.4-2.6 implementation of
        # HTTP(S) has an underlying HTTP(S)Connection, so extract
        # that and use it.
        try:
            response = h.getresponse()
        except AttributeError:
            response = h._conn.getresponse()

        # Add any cookie definitions to our list.
        for header in response.msg.getallmatchingheaders("Set-Cookie"):
            val = header.split(": ", 1)[1]
            cookie = val.split(";", 1)[0]
            self.cookies.append(cookie)

        if response.status != 200:
            raise xmlrpclib.ProtocolError(host + handler, response.status,
                                          response.reason, response.msg.headers)

        payload = response.read()
        parser, unmarshaller = self.getparser()
        parser.feed(payload)
        parser.close()

        return unmarshaller.close()

# The explicit calls to the underlying xmlrpclib __init__() methods are
# necessary. The xmlrpclib.Transport classes are old-style classes, and
# it turns out their __init__() doesn't get called when doing multiple
# inheritance with a new-style class.
class cookietransport(cookietransportrequest, xmlrpclib.Transport):
    def __init__(self, use_datetime=0):
        if util.safehasattr(xmlrpclib.Transport, "__init__"):
            xmlrpclib.Transport.__init__(self, use_datetime)

class cookiesafetransport(cookietransportrequest, xmlrpclib.SafeTransport):
    def __init__(self, use_datetime=0):
        if util.safehasattr(xmlrpclib.Transport, "__init__"):
            xmlrpclib.SafeTransport.__init__(self, use_datetime)

class bzxmlrpc(bzaccess):
    """Support for access to Bugzilla via the Bugzilla XMLRPC API.

    Requires a minimum Bugzilla version 3.4.
    """

    def __init__(self, ui):
        bzaccess.__init__(self, ui)

        bzweb = self.ui.config('bugzilla', 'bzurl',
                               'http://localhost/bugzilla/')
        bzweb = bzweb.rstrip("/") + "/xmlrpc.cgi"

        user = self.ui.config('bugzilla', 'user', 'bugs')
        passwd = self.ui.config('bugzilla', 'password')

        self.fixstatus = self.ui.config('bugzilla', 'fixstatus', 'RESOLVED')
        self.fixresolution = self.ui.config('bugzilla', 'fixresolution',
                                            'FIXED')

        self.bzproxy = xmlrpclib.ServerProxy(bzweb, self.transport(bzweb))
        ver = self.bzproxy.Bugzilla.version()['version'].split('.')
        self.bzvermajor = int(ver[0])
        self.bzverminor = int(ver[1])
        self.bzproxy.User.login(dict(login=user, password=passwd))

    def transport(self, uri):
        if urlparse.urlparse(uri, "http")[0] == "https":
            return cookiesafetransport()
        else:
            return cookietransport()

    def get_bug_comments(self, id):
        """Return a string with all comment text for a bug."""
        c = self.bzproxy.Bug.comments(dict(ids=[id], include_fields=['text']))
        return ''.join([t['text'] for t in c['bugs'][str(id)]['comments']])

    def filter_real_bug_ids(self, bugs):
        probe = self.bzproxy.Bug.get(dict(ids=sorted(bugs.keys()),
                                          include_fields=[],
                                          permissive=True))
        for badbug in probe['faults']:
            id = badbug['id']
            self.ui.status(_('bug %d does not exist\n') % id)
            del bugs[id]

    def filter_cset_known_bug_ids(self, node, bugs):
        for id in sorted(bugs.keys()):
            if self.get_bug_comments(id).find(short(node)) != -1:
                self.ui.status(_('bug %d already knows about changeset %s\n') %
                               (id, short(node)))
                del bugs[id]

    def updatebug(self, bugid, newstate, text, committer):
        args = {}
        if 'hours' in newstate:
            args['work_time'] = newstate['hours']

        if self.bzvermajor >= 4:
            args['ids'] = [bugid]
            args['comment'] = {'body' : text}
            if 'fix' in newstate:
                args['status'] = self.fixstatus
                args['resolution'] = self.fixresolution
            self.bzproxy.Bug.update(args)
        else:
            if 'fix' in newstate:
                self.ui.warn(_("Bugzilla/XMLRPC needs Bugzilla 4.0 or later "
                               "to mark bugs fixed\n"))
            args['id'] = bugid
            args['comment'] = text
            self.bzproxy.Bug.add_comment(args)

class bzxmlrpcemail(bzxmlrpc):
    """Read data from Bugzilla via XMLRPC, send updates via email.

    Advantages of sending updates via email:
      1. Comments can be added as any user, not just logged in user.
      2. Bug statuses or other fields not accessible via XMLRPC can
         potentially be updated.

    There is no XMLRPC function to change bug status before Bugzilla
    4.0, so bugs cannot be marked fixed via XMLRPC before Bugzilla 4.0.
    But bugs can be marked fixed via email from 3.4 onwards.
    """

    # The email interface changes subtly between 3.4 and 3.6. In 3.4,
    # in-email fields are specified as '@<fieldname> = <value>'. In
    # 3.6 this becomes '@<fieldname> <value>'. And fieldname @bug_id
    # in 3.4 becomes @id in 3.6. 3.6 and 4.0 both maintain backwards
    # compatibility, but rather than rely on this use the new format for
    # 4.0 onwards.

    def __init__(self, ui):
        bzxmlrpc.__init__(self, ui)

        self.bzemail = self.ui.config('bugzilla', 'bzemail')
        if not self.bzemail:
            raise util.Abort(_("configuration 'bzemail' missing"))
        mail.validateconfig(self.ui)

    def makecommandline(self, fieldname, value):
        if self.bzvermajor >= 4:
            return "@%s %s" % (fieldname, str(value))
        else:
            if fieldname == "id":
                fieldname = "bug_id"
            return "@%s = %s" % (fieldname, str(value))

    def send_bug_modify_email(self, bugid, commands, comment, committer):
        '''send modification message to Bugzilla bug via email.

        The message format is documented in the Bugzilla email_in.pl
        specification. commands is a list of command lines, comment is the
        comment text.

        To stop users from crafting commit comments with
        Bugzilla commands, specify the bug ID via the message body, rather
        than the subject line, and leave a blank line after it.
        '''
        user = self.map_committer(committer)
        matches = self.bzproxy.User.get(dict(match=[user]))
        if not matches['users']:
            user = self.ui.config('bugzilla', 'user', 'bugs')
            matches = self.bzproxy.User.get(dict(match=[user]))
            if not matches['users']:
                raise util.Abort(_("default bugzilla user %s email not found") %
                                 user)
        user = matches['users'][0]['email']
        commands.append(self.makecommandline("id", bugid))

        text = "\n".join(commands) + "\n\n" + comment

        _charsets = mail._charsets(self.ui)
        user = mail.addressencode(self.ui, user, _charsets)
        bzemail = mail.addressencode(self.ui, self.bzemail, _charsets)
        msg = mail.mimeencode(self.ui, text, _charsets)
        msg['From'] = user
        msg['To'] = bzemail
        msg['Subject'] = mail.headencode(self.ui, "Bug modification", _charsets)
        sendmail = mail.connect(self.ui)
        sendmail(user, bzemail, msg.as_string())

    def updatebug(self, bugid, newstate, text, committer):
        cmds = []
        if 'hours' in newstate:
            cmds.append(self.makecommandline("work_time", newstate['hours']))
        if 'fix' in newstate:
            cmds.append(self.makecommandline("bug_status", self.fixstatus))
            cmds.append(self.makecommandline("resolution", self.fixresolution))
        self.send_bug_modify_email(bugid, cmds, text, committer)

class bugzilla(object):
    # supported versions of bugzilla. different versions have
    # different schemas.
    _versions = {
        '2.16': bzmysql,
        '2.18': bzmysql_2_18,
        '3.0':  bzmysql_3_0,
        'xmlrpc': bzxmlrpc,
        'xmlrpc+email': bzxmlrpcemail
        }

    _default_bug_re = (r'bugs?\s*,?\s*(?:#|nos?\.?|num(?:ber)?s?)?\s*'
                       r'(?P<ids>(?:\d+\s*(?:,?\s*(?:and)?)?\s*)+)'
                       r'\.?\s*(?:h(?:ours?)?\s*(?P<hours>\d*(?:\.\d+)?))?')

    _default_fix_re = (r'fix(?:es)?\s*(?:bugs?\s*)?,?\s*'
                       r'(?:nos?\.?|num(?:ber)?s?)?\s*'
                       r'(?P<ids>(?:#?\d+\s*(?:,?\s*(?:and)?)?\s*)+)'
                       r'\.?\s*(?:h(?:ours?)?\s*(?P<hours>\d*(?:\.\d+)?))?')

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
    _fix_re = None
    _split_re = None

    def find_bugs(self, ctx):
        '''return bugs dictionary created from commit comment.

        Extract bug info from changeset comments. Filter out any that are
        not known to Bugzilla, and any that already have a reference to
        the given changeset in their comments.
        '''
        if bugzilla._bug_re is None:
            bugzilla._bug_re = re.compile(
                self.ui.config('bugzilla', 'regexp',
                               bugzilla._default_bug_re), re.IGNORECASE)
            bugzilla._fix_re = re.compile(
                self.ui.config('bugzilla', 'fixregexp',
                               bugzilla._default_fix_re), re.IGNORECASE)
            bugzilla._split_re = re.compile(r'\D+')
        start = 0
        hours = 0.0
        bugs = {}
        bugmatch = bugzilla._bug_re.search(ctx.description(), start)
        fixmatch = bugzilla._fix_re.search(ctx.description(), start)
        while True:
            bugattribs = {}
            if not bugmatch and not fixmatch:
                break
            if not bugmatch:
                m = fixmatch
            elif not fixmatch:
                m = bugmatch
            else:
                if bugmatch.start() < fixmatch.start():
                    m = bugmatch
                else:
                    m = fixmatch
            start = m.end()
            if m is bugmatch:
                bugmatch = bugzilla._bug_re.search(ctx.description(), start)
                if 'fix' in bugattribs:
                    del bugattribs['fix']
            else:
                fixmatch = bugzilla._fix_re.search(ctx.description(), start)
                bugattribs['fix'] = None

            try:
                ids = m.group('ids')
            except IndexError:
                ids = m.group(1)
            try:
                hours = float(m.group('hours'))
                bugattribs['hours'] = hours
            except IndexError:
                pass
            except TypeError:
                pass
            except ValueError:
                self.ui.status(_("%s: invalid hours\n") % m.group('hours'))

            for id in bugzilla._split_re.split(ids):
                if not id:
                    continue
                bugs[int(id)] = bugattribs
        if bugs:
            self.filter_real_bug_ids(bugs)
        if bugs:
            self.filter_cset_known_bug_ids(ctx.node(), bugs)
        return bugs

    def update(self, bugid, newstate, ctx):
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
        self.updatebug(bugid, newstate, data, util.email(ctx.user()))

def hook(ui, repo, hooktype, node=None, **kwargs):
    '''add comment to bugzilla for each changeset that refers to a
    bugzilla bug id. only add a comment once per bug, so same change
    seen multiple times does not fill bug with duplicate data.'''
    if node is None:
        raise util.Abort(_('hook type %s does not pass a changeset id') %
                         hooktype)
    try:
        bz = bugzilla(ui, repo)
        ctx = repo[node]
        bugs = bz.find_bugs(ctx)
        if bugs:
            for bug in bugs:
                bz.update(bug, bugs[bug], ctx)
            bz.notify(bugs, util.email(ctx.user()))
    except Exception, e:
        raise util.Abort(_('Bugzilla error: %s') % e)
