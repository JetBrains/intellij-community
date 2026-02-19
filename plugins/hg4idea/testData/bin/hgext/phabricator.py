# phabricator.py - simple Phabricator integration
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""simple Phabricator integration (EXPERIMENTAL)

This extension provides a ``phabsend`` command which sends a stack of
changesets to Phabricator, and a ``phabread`` command which prints a stack of
revisions in a format suitable for :hg:`import`, and a ``phabupdate`` command
to update statuses in batch.

A "phabstatus" view for :hg:`show` is also provided; it displays status
information of Phabricator differentials associated with unfinished
changesets.

By default, Phabricator requires ``Test Plan`` which might prevent some
changeset from being sent. The requirement could be disabled by changing
``differential.require-test-plan-field`` config server side.

Config::

    [phabricator]
    # Phabricator URL
    url = https://phab.example.com/

    # Repo callsign. If a repo has a URL https://$HOST/diffusion/FOO, then its
    # callsign is "FOO".
    callsign = FOO

    # curl command to use. If not set (default), use builtin HTTP library to
    # communicate. If set, use the specified curl command. This could be useful
    # if you need to specify advanced options that is not easily supported by
    # the internal library.
    curlcmd = curl --connect-timeout 2 --retry 3 --silent

    # retry failed command N time (default 0). Useful when using the extension
    # over flakly connection.
    #
    # We wait `retry.interval` between each retry, in seconds.
    # (default 1 second).
    retry = 3
    retry.interval = 10

    # the retry option can combine well with the http.timeout one.
    #
    # For example to give up on http request after 20 seconds:
    [http]
    timeout=20

    [auth]
    example.schemes = https
    example.prefix = phab.example.com

    # API token. Get it from https://$HOST/conduit/login/
    example.phabtoken = cli-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
"""


import base64
import contextlib
import hashlib
import io
import itertools
import json
import mimetypes
import operator
import re
import time

from mercurial.node import bin, short
from mercurial.i18n import _
from mercurial.thirdparty import attr
from mercurial import (
    cmdutil,
    context,
    copies,
    encoding,
    error,
    exthelper,
    graphmod,
    httpconnection as httpconnectionmod,
    localrepo,
    logcmdutil,
    match,
    mdiff,
    obsutil,
    parser,
    patch,
    phases,
    pycompat,
    rewriteutil,
    scmutil,
    smartset,
    tags,
    templatefilters,
    templateutil,
    url as urlmod,
    util,
)
from mercurial.utils import (
    procutil,
    stringutil,
    urlutil,
)
from . import show


# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

eh = exthelper.exthelper()

cmdtable = eh.cmdtable
command = eh.command
configtable = eh.configtable
templatekeyword = eh.templatekeyword
uisetup = eh.finaluisetup

# developer config: phabricator.batchsize
eh.configitem(
    b'phabricator',
    b'batchsize',
    default=12,
)
eh.configitem(
    b'phabricator',
    b'callsign',
    default=None,
)
eh.configitem(
    b'phabricator',
    b'curlcmd',
    default=None,
)
# developer config: phabricator.debug
eh.configitem(
    b'phabricator',
    b'debug',
    default=False,
)
# developer config: phabricator.repophid
eh.configitem(
    b'phabricator',
    b'repophid',
    default=None,
)
eh.configitem(
    b'phabricator',
    b'retry',
    default=0,
)
eh.configitem(
    b'phabricator',
    b'retry.interval',
    default=1,
)
eh.configitem(
    b'phabricator',
    b'url',
    default=None,
)
eh.configitem(
    b'phabsend',
    b'confirm',
    default=False,
)
eh.configitem(
    b'phabimport',
    b'secret',
    default=False,
)
eh.configitem(
    b'phabimport',
    b'obsolete',
    default=False,
)

colortable = {
    b'phabricator.action.created': b'green',
    b'phabricator.action.skipped': b'magenta',
    b'phabricator.action.updated': b'magenta',
    b'phabricator.drev': b'bold',
    b'phabricator.status.abandoned': b'magenta dim',
    b'phabricator.status.accepted': b'green bold',
    b'phabricator.status.closed': b'green',
    b'phabricator.status.needsreview': b'yellow',
    b'phabricator.status.needsrevision': b'red',
    b'phabricator.status.changesplanned': b'red',
}

_VCR_FLAGS = [
    (
        b'',
        b'test-vcr',
        b'',
        _(
            b'Path to a vcr file. If nonexistent, will record a new vcr transcript'
            b', otherwise will mock all http requests using the specified vcr file.'
            b' (ADVANCED)'
        ),
    ),
]


@eh.wrapfunction(localrepo, "loadhgrc")
def _loadhgrc(orig, ui, wdirvfs, hgvfs, requirements, *args, **opts):
    """Load ``.arcconfig`` content into a ui instance on repository open."""
    result = False
    arcconfig = {}

    try:
        # json.loads only accepts bytes from 3.6+
        rawparams = encoding.unifromlocal(wdirvfs.read(b".arcconfig"))
        # json.loads only returns unicode strings
        arcconfig = pycompat.rapply(
            lambda x: encoding.unitolocal(x) if isinstance(x, str) else x,
            pycompat.json_loads(rawparams),
        )

        result = True
    except ValueError:
        ui.warn(_(b"invalid JSON in %s\n") % wdirvfs.join(b".arcconfig"))
    except IOError:
        pass

    cfg = util.sortdict()

    if b"repository.callsign" in arcconfig:
        cfg[(b"phabricator", b"callsign")] = arcconfig[b"repository.callsign"]

    if b"phabricator.uri" in arcconfig:
        cfg[(b"phabricator", b"url")] = arcconfig[b"phabricator.uri"]

    if cfg:
        ui.applyconfig(cfg, source=wdirvfs.join(b".arcconfig"))

    return (
        orig(ui, wdirvfs, hgvfs, requirements, *args, **opts) or result
    )  # Load .hg/hgrc


def vcrcommand(name, flags, spec, helpcategory=None, optionalrepo=False):
    fullflags = flags + _VCR_FLAGS

    def hgmatcher(r1, r2):
        if r1.uri != r2.uri or r1.method != r2.method:
            return False
        r1params = util.urlreq.parseqs(r1.body)
        r2params = util.urlreq.parseqs(r2.body)
        for key in r1params:
            if key not in r2params:
                return False
            value = r1params[key][0]
            # we want to compare json payloads without worrying about ordering
            if value.startswith(b'{') and value.endswith(b'}'):
                r1json = pycompat.json_loads(value)
                r2json = pycompat.json_loads(r2params[key][0])
                if r1json != r2json:
                    return False
            elif r2params[key][0] != value:
                return False
        return True

    def sanitiserequest(request):
        request.body = re.sub(
            br'cli-[a-z0-9]+', br'cli-hahayouwish', request.body
        )
        return request

    def sanitiseresponse(response):
        if 'set-cookie' in response['headers']:
            del response['headers']['set-cookie']
        return response

    def decorate(fn):
        def inner(*args, **kwargs):
            vcr = kwargs.pop('test_vcr')
            if vcr:
                cassette = pycompat.fsdecode(vcr)
                import hgdemandimport

                with hgdemandimport.deactivated():
                    # pytype: disable=import-error
                    import vcr as vcrmod
                    import vcr.stubs as stubs

                    # pytype: enable=import-error

                    vcr = vcrmod.VCR(
                        serializer='json',
                        before_record_request=sanitiserequest,
                        before_record_response=sanitiseresponse,
                        custom_patches=[
                            (
                                urlmod,
                                'httpconnection',
                                stubs.VCRHTTPConnection,
                            ),
                            (
                                urlmod,
                                'httpsconnection',
                                stubs.VCRHTTPSConnection,
                            ),
                        ],
                    )
                    vcr.register_matcher('hgmatcher', hgmatcher)
                    with vcr.use_cassette(cassette, match_on=['hgmatcher']):
                        return fn(*args, **kwargs)
            return fn(*args, **kwargs)

        cmd = util.checksignature(inner, depth=2)
        cmd.__name__ = fn.__name__
        cmd.__doc__ = fn.__doc__

        return command(
            name,
            fullflags,
            spec,
            helpcategory=helpcategory,
            optionalrepo=optionalrepo,
        )(cmd)

    return decorate


def _debug(ui, *msg, **opts):
    """write debug output for Phabricator if ``phabricator.debug`` is set

    Specifically, this avoids dumping Conduit and HTTP auth chatter that is
    printed with the --debug argument.
    """
    if ui.configbool(b"phabricator", b"debug"):
        flag = ui.debugflag
        try:
            ui.debugflag = True
            ui.write(*msg, **opts)
        finally:
            ui.debugflag = flag


def urlencodenested(params):
    """like urlencode, but works with nested parameters.

    For example, if params is {'a': ['b', 'c'], 'd': {'e': 'f'}}, it will be
    flattened to {'a[0]': 'b', 'a[1]': 'c', 'd[e]': 'f'} and then passed to
    urlencode. Note: the encoding is consistent with PHP's http_build_query.
    """
    flatparams = util.sortdict()

    def process(prefix: bytes, obj):
        if isinstance(obj, bool):
            obj = {True: b'true', False: b'false'}[obj]  # Python -> PHP form
        lister = lambda l: [(b'%d' % k, v) for k, v in enumerate(l)]
        # .items() will only be called for a dict type
        # pytype: disable=attribute-error
        items = {list: lister, dict: lambda x: x.items()}.get(type(obj))
        # pytype: enable=attribute-error
        if items is None:
            flatparams[prefix] = obj
        else:
            for k, v in items(obj):
                if prefix:
                    process(b'%s[%s]' % (prefix, k), v)
                else:
                    process(k, v)

    process(b'', params)
    return urlutil.urlreq.urlencode(flatparams)


def readurltoken(ui):
    """return conduit url, token and make sure they exist

    Currently read from [auth] config section. In the future, it might
    make sense to read from .arcconfig and .arcrc as well.
    """
    url = ui.config(b'phabricator', b'url')
    if not url:
        raise error.Abort(
            _(b'config %s.%s is required') % (b'phabricator', b'url')
        )

    res = httpconnectionmod.readauthforuri(ui, url, urlutil.url(url).user)
    token = None

    if res:
        group, auth = res

        ui.debug(b"using auth.%s.* for authentication\n" % group)

        token = auth.get(b'phabtoken')

    if not token:
        raise error.Abort(
            _(b'Can\'t find conduit token associated to %s') % (url,)
        )

    return url, token


def callconduit(ui, name, params):
    """call Conduit API, params is a dict. return json.loads result, or None"""
    host, token = readurltoken(ui)
    url, authinfo = urlutil.url(b'/'.join([host, b'api', name])).authinfo()
    ui.debug(b'Conduit Call: %s %s\n' % (url, pycompat.byterepr(params)))
    params = params.copy()
    params[b'__conduit__'] = {
        b'token': token,
    }
    rawdata = {
        b'params': templatefilters.json(params),
        b'output': b'json',
        b'__conduit__': 1,
    }
    data = urlencodenested(rawdata)
    curlcmd = ui.config(b'phabricator', b'curlcmd')
    if curlcmd:
        sin, sout = procutil.popen2(
            b'%s -d @- %s' % (curlcmd, procutil.shellquote(url))
        )
        sin.write(data)
        sin.close()
        body = sout.read()
    else:
        urlopener = urlmod.opener(ui, authinfo)
        request = util.urlreq.request(pycompat.strurl(url), data=data)
        max_try = ui.configint(b'phabricator', b'retry') + 1
        timeout = ui.configwith(float, b'http', b'timeout')
        for try_count in range(max_try):
            try:
                with contextlib.closing(
                    urlopener.open(request, timeout=timeout)
                ) as rsp:
                    body = rsp.read()
                break
            except util.urlerr.urlerror as err:
                if try_count == max_try - 1:
                    raise
                ui.debug(
                    b'Conduit Request failed (try %d/%d): %r\n'
                    % (try_count + 1, max_try, err)
                )
                # failing request might come from overloaded server
                retry_interval = ui.configint(b'phabricator', b'retry.interval')
                time.sleep(retry_interval)
    ui.debug(b'Conduit Response: %s\n' % body)
    parsed = pycompat.rapply(
        lambda x: encoding.unitolocal(x) if isinstance(x, str) else x,
        # json.loads only accepts bytes from py3.6+
        pycompat.json_loads(encoding.unifromlocal(body)),
    )
    if parsed.get(b'error_code'):
        msg = _(b'Conduit Error (%s): %s') % (
            parsed[b'error_code'],
            parsed[b'error_info'],
        )
        raise error.Abort(msg)
    return parsed[b'result']


@vcrcommand(b'debugcallconduit', [], _(b'METHOD'), optionalrepo=True)
def debugcallconduit(ui, repo, name):
    """call Conduit API

    Call parameters are read from stdin as a JSON blob. Result will be written
    to stdout as a JSON blob.
    """
    # json.loads only accepts bytes from 3.6+
    rawparams = encoding.unifromlocal(ui.fin.read())
    # json.loads only returns unicode strings
    params = pycompat.rapply(
        lambda x: encoding.unitolocal(x) if isinstance(x, str) else x,
        pycompat.json_loads(rawparams),
    )
    # json.dumps only accepts unicode strings
    result = pycompat.rapply(
        lambda x: encoding.unifromlocal(x) if isinstance(x, bytes) else x,
        callconduit(ui, name, params),
    )
    s = json.dumps(result, sort_keys=True, indent=2, separators=(u',', u': '))
    ui.write(b'%s\n' % encoding.unitolocal(s))


def getrepophid(repo):
    """given callsign, return repository PHID or None"""
    # developer config: phabricator.repophid
    repophid = repo.ui.config(b'phabricator', b'repophid')
    if repophid:
        return repophid
    callsign = repo.ui.config(b'phabricator', b'callsign')
    if not callsign:
        return None
    query = callconduit(
        repo.ui,
        b'diffusion.repository.search',
        {b'constraints': {b'callsigns': [callsign]}},
    )
    if len(query[b'data']) == 0:
        return None
    repophid = query[b'data'][0][b'phid']
    repo.ui.setconfig(b'phabricator', b'repophid', repophid)
    return repophid


_differentialrevisiontagre = re.compile(br'\AD([1-9][0-9]*)\Z')
_differentialrevisiondescre = re.compile(
    br'^Differential Revision:\s*(?P<url>(?:.*)D(?P<id>[1-9][0-9]*))$', re.M
)


def getoldnodedrevmap(repo, nodelist):
    """find previous nodes that has been sent to Phabricator

    return {node: (oldnode, Differential diff, Differential Revision ID)}
    for node in nodelist with known previous sent versions, or associated
    Differential Revision IDs. ``oldnode`` and ``Differential diff`` could
    be ``None``.

    Examines commit messages like "Differential Revision:" to get the
    association information.

    If such commit message line is not found, examines all precursors and their
    tags. Tags with format like "D1234" are considered a match and the node
    with that tag, and the number after "D" (ex. 1234) will be returned.

    The ``old node``, if not None, is guaranteed to be the last diff of
    corresponding Differential Revision, and exist in the repo.
    """
    unfi = repo.unfiltered()
    has_node = unfi.changelog.index.has_node

    result = {}  # {node: (oldnode?, lastdiff?, drev)}
    # ordered for test stability when printing new -> old mapping below
    toconfirm = util.sortdict()  # {node: (force, {precnode}, drev)}
    for node in nodelist:
        ctx = unfi[node]
        # For tags like "D123", put them into "toconfirm" to verify later
        precnodes = list(obsutil.allpredecessors(unfi.obsstore, [node]))
        for n in precnodes:
            if has_node(n):
                for tag in unfi.nodetags(n):
                    m = _differentialrevisiontagre.match(tag)
                    if m:
                        toconfirm[node] = (0, set(precnodes), int(m.group(1)))
                        break
                else:
                    continue  # move to next predecessor
                break  # found a tag, stop
        else:
            # Check commit message
            m = _differentialrevisiondescre.search(ctx.description())
            if m:
                toconfirm[node] = (1, set(precnodes), int(m.group('id')))

    # Double check if tags are genuine by collecting all old nodes from
    # Phabricator, and expect precursors overlap with it.
    if toconfirm:
        drevs = [drev for force, precs, drev in toconfirm.values()]
        alldiffs = callconduit(
            unfi.ui, b'differential.querydiffs', {b'revisionIDs': drevs}
        )

        def getnodes(d, precset):
            # Ignore other nodes that were combined into the Differential
            # that aren't predecessors of the current local node.
            return [n for n in getlocalcommits(d) if n in precset]

        for newnode, (force, precset, drev) in toconfirm.items():
            diffs = [
                d for d in alldiffs.values() if int(d[b'revisionID']) == drev
            ]

            # local predecessors known by Phabricator
            phprecset = {n for d in diffs for n in getnodes(d, precset)}

            # Ignore if precursors (Phabricator and local repo) do not overlap,
            # and force is not set (when commit message says nothing)
            if not force and not phprecset:
                tagname = b'D%d' % drev
                tags.tag(
                    repo,
                    tagname,
                    repo.nullid,
                    message=None,
                    user=None,
                    date=None,
                    local=True,
                )
                unfi.ui.warn(
                    _(
                        b'D%d: local tag removed - does not match '
                        b'Differential history\n'
                    )
                    % drev
                )
                continue

            # Find the last node using Phabricator metadata, and make sure it
            # exists in the repo
            oldnode = lastdiff = None
            if diffs:
                lastdiff = max(diffs, key=lambda d: int(d[b'id']))
                oldnodes = getnodes(lastdiff, precset)

                _debug(
                    unfi.ui,
                    b"%s mapped to old nodes %s\n"
                    % (
                        short(newnode),
                        stringutil.pprint([short(n) for n in sorted(oldnodes)]),
                    ),
                )

                # If this commit was the result of `hg fold` after submission,
                # and now resubmitted with --fold, the easiest thing to do is
                # to leave the node clear.  This only results in creating a new
                # diff for the _same_ Differential Revision if this commit is
                # the first or last in the selected range.  If we picked a node
                # from the list instead, it would have to be the lowest if at
                # the beginning of the --fold range, or the highest at the end.
                # Otherwise, one or more of the nodes wouldn't be considered in
                # the diff, and the Differential wouldn't be properly updated.
                # If this commit is the result of `hg split` in the same
                # scenario, there is a single oldnode here (and multiple
                # newnodes mapped to it).  That makes it the same as the normal
                # case, as the edges of the newnode range cleanly maps to one
                # oldnode each.
                if len(oldnodes) == 1:
                    oldnode = oldnodes[0]
                if oldnode and not has_node(oldnode):
                    oldnode = None

            result[newnode] = (oldnode, lastdiff, drev)

    return result


def getdrevmap(repo, revs):
    """Return a dict mapping each rev in `revs` to their Differential Revision
    ID or None.
    """
    result = {}
    for rev in revs:
        result[rev] = None
        ctx = repo[rev]
        # Check commit message
        m = _differentialrevisiondescre.search(ctx.description())
        if m:
            result[rev] = int(m.group('id'))
            continue
        # Check tags
        for tag in repo.nodetags(ctx.node()):
            m = _differentialrevisiontagre.match(tag)
            if m:
                result[rev] = int(m.group(1))
                break

    return result


def getdiff(basectx, ctx, diffopts):
    """plain-text diff without header (user, commit message, etc)"""
    output = util.stringio()
    for chunk, _label in patch.diffui(
        ctx.repo(), basectx.p1().node(), ctx.node(), None, opts=diffopts
    ):
        output.write(chunk)
    return output.getvalue()


class DiffChangeType:
    ADD = 1
    CHANGE = 2
    DELETE = 3
    MOVE_AWAY = 4
    COPY_AWAY = 5
    MOVE_HERE = 6
    COPY_HERE = 7
    MULTICOPY = 8


class DiffFileType:
    TEXT = 1
    IMAGE = 2
    BINARY = 3


@attr.s
class phabhunk(dict):
    """Represents a Differential hunk, which is owned by a Differential change"""

    oldOffset = attr.ib(default=0)  # camelcase-required
    oldLength = attr.ib(default=0)  # camelcase-required
    newOffset = attr.ib(default=0)  # camelcase-required
    newLength = attr.ib(default=0)  # camelcase-required
    corpus = attr.ib(default='')
    # These get added to the phabchange's equivalents
    addLines = attr.ib(default=0)  # camelcase-required
    delLines = attr.ib(default=0)  # camelcase-required


@attr.s
class phabchange:
    """Represents a Differential change, owns Differential hunks and owned by a
    Differential diff.  Each one represents one file in a diff.
    """

    currentPath = attr.ib(default=None)  # camelcase-required
    oldPath = attr.ib(default=None)  # camelcase-required
    awayPaths = attr.ib(default=attr.Factory(list))  # camelcase-required
    metadata = attr.ib(default=attr.Factory(dict))
    oldProperties = attr.ib(default=attr.Factory(dict))  # camelcase-required
    newProperties = attr.ib(default=attr.Factory(dict))  # camelcase-required
    type = attr.ib(default=DiffChangeType.CHANGE)
    fileType = attr.ib(default=DiffFileType.TEXT)  # camelcase-required
    commitHash = attr.ib(default=None)  # camelcase-required
    addLines = attr.ib(default=0)  # camelcase-required
    delLines = attr.ib(default=0)  # camelcase-required
    hunks = attr.ib(default=attr.Factory(list))

    def copynewmetadatatoold(self):
        for key in list(self.metadata.keys()):
            newkey = key.replace(b'new:', b'old:')
            self.metadata[newkey] = self.metadata[key]

    def addoldmode(self, value):
        self.oldProperties[b'unix:filemode'] = value

    def addnewmode(self, value):
        self.newProperties[b'unix:filemode'] = value

    def addhunk(self, hunk):
        if not isinstance(hunk, phabhunk):
            raise error.Abort(b'phabchange.addhunk only takes phabhunks')
        self.hunks.append(pycompat.byteskwargs(attr.asdict(hunk)))
        # It's useful to include these stats since the Phab web UI shows them,
        # and uses them to estimate how large a change a Revision is. Also used
        # in email subjects for the [+++--] bit.
        self.addLines += hunk.addLines
        self.delLines += hunk.delLines


@attr.s
class phabdiff:
    """Represents a Differential diff, owns Differential changes.  Corresponds
    to a commit.
    """

    # Doesn't seem to be any reason to send this (output of uname -n)
    sourceMachine = attr.ib(default=b'')  # camelcase-required
    sourcePath = attr.ib(default=b'/')  # camelcase-required
    sourceControlBaseRevision = attr.ib(default=b'0' * 40)  # camelcase-required
    sourceControlPath = attr.ib(default=b'/')  # camelcase-required
    sourceControlSystem = attr.ib(default=b'hg')  # camelcase-required
    branch = attr.ib(default=b'default')
    bookmark = attr.ib(default=None)
    creationMethod = attr.ib(default=b'phabsend')  # camelcase-required
    lintStatus = attr.ib(default=b'none')  # camelcase-required
    unitStatus = attr.ib(default=b'none')  # camelcase-required
    changes = attr.ib(default=attr.Factory(dict))
    repositoryPHID = attr.ib(default=None)  # camelcase-required

    def addchange(self, change):
        if not isinstance(change, phabchange):
            raise error.Abort(b'phabdiff.addchange only takes phabchanges')
        self.changes[change.currentPath] = pycompat.byteskwargs(
            attr.asdict(change)
        )


def maketext(pchange, basectx, ctx, fname):
    """populate the phabchange for a text file"""
    repo = ctx.repo()
    fmatcher = match.exact([fname])
    diffopts = mdiff.diffopts(git=True, context=32767)
    _pfctx, _fctx, header, fhunks = next(
        patch.diffhunks(repo, basectx.p1(), ctx, fmatcher, opts=diffopts)
    )

    for fhunk in fhunks:
        (oldOffset, oldLength, newOffset, newLength), lines = fhunk
        corpus = b''.join(lines[1:])
        shunk = list(header)
        shunk.extend(lines)
        _mf, _mt, addLines, delLines, _hb = patch.diffstatsum(
            patch.diffstatdata(util.iterlines(shunk))
        )
        pchange.addhunk(
            phabhunk(
                oldOffset,
                oldLength,
                newOffset,
                newLength,
                corpus,
                addLines,
                delLines,
            )
        )


def uploadchunks(fctx, fphid):
    """upload large binary files as separate chunks.
    Phab requests chunking over 8MiB, and splits into 4MiB chunks
    """
    ui = fctx.repo().ui
    chunks = callconduit(ui, b'file.querychunks', {b'filePHID': fphid})
    with ui.makeprogress(
        _(b'uploading file chunks'), unit=_(b'chunks'), total=len(chunks)
    ) as progress:
        for chunk in chunks:
            progress.increment()
            if chunk[b'complete']:
                continue
            bstart = int(chunk[b'byteStart'])
            bend = int(chunk[b'byteEnd'])
            callconduit(
                ui,
                b'file.uploadchunk',
                {
                    b'filePHID': fphid,
                    b'byteStart': bstart,
                    b'data': base64.b64encode(fctx.data()[bstart:bend]),
                    b'dataEncoding': b'base64',
                },
            )


def uploadfile(fctx):
    """upload binary files to Phabricator"""
    repo = fctx.repo()
    ui = repo.ui
    fname = fctx.path()
    size = fctx.size()
    fhash = pycompat.bytestr(hashlib.sha256(fctx.data()).hexdigest())

    # an allocate call is required first to see if an upload is even required
    # (Phab might already have it) and to determine if chunking is needed
    allocateparams = {
        b'name': fname,
        b'contentLength': size,
        b'contentHash': fhash,
    }
    filealloc = callconduit(ui, b'file.allocate', allocateparams)
    fphid = filealloc[b'filePHID']

    if filealloc[b'upload']:
        ui.write(_(b'uploading %s\n') % bytes(fctx))
        if not fphid:
            uploadparams = {
                b'name': fname,
                b'data_base64': base64.b64encode(fctx.data()),
            }
            fphid = callconduit(ui, b'file.upload', uploadparams)
        else:
            uploadchunks(fctx, fphid)
    else:
        ui.debug(b'server already has %s\n' % bytes(fctx))

    if not fphid:
        raise error.Abort(b'Upload of %s failed.' % bytes(fctx))

    return fphid


def addoldbinary(pchange, oldfctx, fctx):
    """add the metadata for the previous version of a binary file to the
    phabchange for the new version

    ``oldfctx`` is the previous version of the file; ``fctx`` is the new
    version of the file, or None if the file is being removed.
    """
    if not fctx or fctx.cmp(oldfctx):
        # Files differ, add the old one
        pchange.metadata[b'old:file:size'] = oldfctx.size()
        mimeguess, _enc = mimetypes.guess_type(
            encoding.unifromlocal(oldfctx.path())
        )
        if mimeguess:
            pchange.metadata[b'old:file:mime-type'] = pycompat.bytestr(
                mimeguess
            )
        fphid = uploadfile(oldfctx)
        pchange.metadata[b'old:binary-phid'] = fphid
    else:
        # If it's left as IMAGE/BINARY web UI might try to display it
        pchange.fileType = DiffFileType.TEXT
        pchange.copynewmetadatatoold()


def makebinary(pchange, fctx):
    """populate the phabchange for a binary file"""
    pchange.fileType = DiffFileType.BINARY
    fphid = uploadfile(fctx)
    pchange.metadata[b'new:binary-phid'] = fphid
    pchange.metadata[b'new:file:size'] = fctx.size()
    mimeguess, _enc = mimetypes.guess_type(encoding.unifromlocal(fctx.path()))
    if mimeguess:
        mimeguess = pycompat.bytestr(mimeguess)
        pchange.metadata[b'new:file:mime-type'] = mimeguess
        if mimeguess.startswith(b'image/'):
            pchange.fileType = DiffFileType.IMAGE


# Copied from mercurial/patch.py
gitmode = {b'l': b'120000', b'x': b'100755', b'': b'100644'}


def notutf8(fctx):
    """detect non-UTF-8 text files since Phabricator requires them to be marked
    as binary
    """
    try:
        fctx.data().decode('utf-8')
        return False
    except UnicodeDecodeError:
        fctx.repo().ui.write(
            _(b'file %s detected as non-UTF-8, marked as binary\n')
            % fctx.path()
        )
        return True


def addremoved(pdiff, basectx, ctx, removed):
    """add removed files to the phabdiff. Shouldn't include moves"""
    for fname in removed:
        pchange = phabchange(
            currentPath=fname, oldPath=fname, type=DiffChangeType.DELETE
        )
        oldfctx = basectx.p1()[fname]
        pchange.addoldmode(gitmode[oldfctx.flags()])
        if not (oldfctx.isbinary() or notutf8(oldfctx)):
            maketext(pchange, basectx, ctx, fname)

        pdiff.addchange(pchange)


def addmodified(pdiff, basectx, ctx, modified):
    """add modified files to the phabdiff"""
    for fname in modified:
        fctx = ctx[fname]
        oldfctx = basectx.p1()[fname]
        pchange = phabchange(currentPath=fname, oldPath=fname)
        filemode = gitmode[fctx.flags()]
        originalmode = gitmode[oldfctx.flags()]
        if filemode != originalmode:
            pchange.addoldmode(originalmode)
            pchange.addnewmode(filemode)

        if (
            fctx.isbinary()
            or notutf8(fctx)
            or oldfctx.isbinary()
            or notutf8(oldfctx)
        ):
            makebinary(pchange, fctx)
            addoldbinary(pchange, oldfctx, fctx)
        else:
            maketext(pchange, basectx, ctx, fname)

        pdiff.addchange(pchange)


def addadded(pdiff, basectx, ctx, added, removed):
    """add file adds to the phabdiff, both new files and copies/moves"""
    # Keep track of files that've been recorded as moved/copied, so if there are
    # additional copies we can mark them (moves get removed from removed)
    copiedchanges = {}
    movedchanges = {}

    copy = {}
    if basectx != ctx:
        copy = copies.pathcopies(basectx.p1(), ctx)

    for fname in added:
        fctx = ctx[fname]
        oldfctx = None
        pchange = phabchange(currentPath=fname)

        filemode = gitmode[fctx.flags()]

        if copy:
            originalfname = copy.get(fname, fname)
        else:
            originalfname = fname
            if fctx.renamed():
                originalfname = fctx.renamed()[0]

        renamed = fname != originalfname

        if renamed:
            oldfctx = basectx.p1()[originalfname]
            originalmode = gitmode[oldfctx.flags()]
            pchange.oldPath = originalfname

            if originalfname in removed:
                origpchange = phabchange(
                    currentPath=originalfname,
                    oldPath=originalfname,
                    type=DiffChangeType.MOVE_AWAY,
                    awayPaths=[fname],
                )
                movedchanges[originalfname] = origpchange
                removed.remove(originalfname)
                pchange.type = DiffChangeType.MOVE_HERE
            elif originalfname in movedchanges:
                movedchanges[originalfname].type = DiffChangeType.MULTICOPY
                movedchanges[originalfname].awayPaths.append(fname)
                pchange.type = DiffChangeType.COPY_HERE
            else:  # pure copy
                if originalfname not in copiedchanges:
                    origpchange = phabchange(
                        currentPath=originalfname, type=DiffChangeType.COPY_AWAY
                    )
                    copiedchanges[originalfname] = origpchange
                else:
                    origpchange = copiedchanges[originalfname]
                origpchange.awayPaths.append(fname)
                pchange.type = DiffChangeType.COPY_HERE

            if filemode != originalmode:
                pchange.addoldmode(originalmode)
                pchange.addnewmode(filemode)
        else:  # Brand-new file
            pchange.addnewmode(gitmode[fctx.flags()])
            pchange.type = DiffChangeType.ADD

        if (
            fctx.isbinary()
            or notutf8(fctx)
            or (oldfctx and (oldfctx.isbinary() or notutf8(oldfctx)))
        ):
            makebinary(pchange, fctx)
            if renamed:
                addoldbinary(pchange, oldfctx, fctx)
        else:
            maketext(pchange, basectx, ctx, fname)

        pdiff.addchange(pchange)

    for _path, copiedchange in copiedchanges.items():
        pdiff.addchange(copiedchange)
    for _path, movedchange in movedchanges.items():
        pdiff.addchange(movedchange)


def creatediff(basectx, ctx):
    """create a Differential Diff"""
    repo = ctx.repo()
    repophid = getrepophid(repo)
    # Create a "Differential Diff" via "differential.creatediff" API
    pdiff = phabdiff(
        sourceControlBaseRevision=b'%s' % basectx.p1().hex(),
        branch=b'%s' % ctx.branch(),
    )
    modified, added, removed, _d, _u, _i, _c = basectx.p1().status(ctx)
    # addadded will remove moved files from removed, so addremoved won't get
    # them
    addadded(pdiff, basectx, ctx, added, removed)
    addmodified(pdiff, basectx, ctx, modified)
    addremoved(pdiff, basectx, ctx, removed)
    if repophid:
        pdiff.repositoryPHID = repophid
    diff = callconduit(
        repo.ui,
        b'differential.creatediff',
        pycompat.byteskwargs(attr.asdict(pdiff)),
    )
    if not diff:
        if basectx != ctx:
            msg = _(b'cannot create diff for %s::%s') % (basectx, ctx)
        else:
            msg = _(b'cannot create diff for %s') % ctx
        raise error.Abort(msg)
    return diff


def writediffproperties(ctxs, diff):
    """write metadata to diff so patches could be applied losslessly

    ``ctxs`` is the list of commits that created the diff, in ascending order.
    The list is generally a single commit, but may be several when using
    ``phabsend --fold``.
    """
    # creatediff returns with a diffid but query returns with an id
    diffid = diff.get(b'diffid', diff.get(b'id'))
    basectx = ctxs[0]
    tipctx = ctxs[-1]

    params = {
        b'diff_id': diffid,
        b'name': b'hg:meta',
        b'data': templatefilters.json(
            {
                b'user': tipctx.user(),
                b'date': b'%d %d' % tipctx.date(),
                b'branch': tipctx.branch(),
                b'node': tipctx.hex(),
                b'parent': basectx.p1().hex(),
            }
        ),
    }
    callconduit(basectx.repo().ui, b'differential.setdiffproperty', params)

    commits = {}
    for ctx in ctxs:
        commits[ctx.hex()] = {
            b'author': stringutil.person(ctx.user()),
            b'authorEmail': stringutil.email(ctx.user()),
            b'time': int(ctx.date()[0]),
            b'commit': ctx.hex(),
            b'parents': [ctx.p1().hex()],
            b'branch': ctx.branch(),
        }
    params = {
        b'diff_id': diffid,
        b'name': b'local:commits',
        b'data': templatefilters.json(commits),
    }
    callconduit(basectx.repo().ui, b'differential.setdiffproperty', params)


def createdifferentialrevision(
    ctxs,
    revid=None,
    parentrevphid=None,
    oldbasenode=None,
    oldnode=None,
    olddiff=None,
    actions=None,
    comment=None,
):
    """create or update a Differential Revision

    If revid is None, create a new Differential Revision, otherwise update
    revid. If parentrevphid is not None, set it as a dependency.

    If there is a single commit for the new Differential Revision, ``ctxs`` will
    be a list of that single context.  Otherwise, it is a list that covers the
    range of changes for the differential, where ``ctxs[0]`` is the first change
    to include and ``ctxs[-1]`` is the last.

    If oldnode is not None, check if the patch content (without commit message
    and metadata) has changed before creating another diff.  For a Revision with
    a single commit, ``oldbasenode`` and ``oldnode`` have the same value.  For a
    Revision covering multiple commits, ``oldbasenode`` corresponds to
    ``ctxs[0]`` the previous time this Revision was posted, and ``oldnode``
    corresponds to ``ctxs[-1]``.

    If actions is not None, they will be appended to the transaction.
    """
    ctx = ctxs[-1]
    basectx = ctxs[0]

    repo = ctx.repo()
    if oldnode:
        diffopts = mdiff.diffopts(git=True, context=32767)
        unfi = repo.unfiltered()
        oldctx = unfi[oldnode]
        oldbasectx = unfi[oldbasenode]
        neednewdiff = getdiff(basectx, ctx, diffopts) != getdiff(
            oldbasectx, oldctx, diffopts
        )
    else:
        neednewdiff = True

    transactions = []
    if neednewdiff:
        diff = creatediff(basectx, ctx)
        transactions.append({b'type': b'update', b'value': diff[b'phid']})
        if comment:
            transactions.append({b'type': b'comment', b'value': comment})
    else:
        # Even if we don't need to upload a new diff because the patch content
        # does not change. We might still need to update its metadata so
        # pushers could know the correct node metadata.
        assert olddiff
        diff = olddiff
    writediffproperties(ctxs, diff)

    # Set the parent Revision every time, so commit re-ordering is picked-up
    if parentrevphid:
        transactions.append(
            {b'type': b'parents.set', b'value': [parentrevphid]}
        )

    if actions:
        transactions += actions

    # When folding multiple local commits into a single review, arcanist will
    # take the summary line of the first commit as the title, and then
    # concatenate the rest of the remaining messages (including each of their
    # first lines) to the rest of the first commit message (each separated by
    # an empty line), and use that as the summary field.  Do the same here.
    # For commits with only a one line message, there is no summary field, as
    # this gets assigned to the title.
    fields = util.sortdict()  # sorted for stable wire protocol in tests

    for i, _ctx in enumerate(ctxs):
        # Parse commit message and update related fields.
        desc = _ctx.description()
        info = callconduit(
            repo.ui, b'differential.parsecommitmessage', {b'corpus': desc}
        )

        for k in [b'title', b'summary', b'testPlan']:
            v = info[b'fields'].get(k)
            if not v:
                continue

            if i == 0:
                # Title, summary and test plan (if present) are taken verbatim
                # for the first commit.
                fields[k] = v.rstrip()
                continue
            elif k == b'title':
                # Add subsequent titles (i.e. the first line of the commit
                # message) back to the summary.
                k = b'summary'

            # Append any current field to the existing composite field
            fields[k] = b'\n\n'.join(filter(None, [fields.get(k), v.rstrip()]))

    for k, v in fields.items():
        transactions.append({b'type': k, b'value': v})

    params = {b'transactions': transactions}
    if revid is not None:
        # Update an existing Differential Revision
        params[b'objectIdentifier'] = revid

    revision = callconduit(repo.ui, b'differential.revision.edit', params)
    if not revision:
        if len(ctxs) == 1:
            msg = _(b'cannot create revision for %s') % ctx
        else:
            msg = _(b'cannot create revision for %s::%s') % (basectx, ctx)
        raise error.Abort(msg)

    return revision, diff


def userphids(ui, names):
    """convert user names to PHIDs"""
    names = [name.lower() for name in names]
    query = {b'constraints': {b'usernames': names}}
    result = callconduit(ui, b'user.search', query)
    # username not found is not an error of the API. So check if we have missed
    # some names here.
    data = result[b'data']
    resolved = {entry[b'fields'][b'username'].lower() for entry in data}
    unresolved = set(names) - resolved
    if unresolved:
        raise error.Abort(
            _(b'unknown username: %s') % b' '.join(sorted(unresolved))
        )
    return [entry[b'phid'] for entry in data]


def _print_phabsend_action(ui, ctx, newrevid, action):
    """print the ``action`` that occurred when posting ``ctx`` for review

    This is a utility function for the sending phase of ``phabsend``, which
    makes it easier to show a status for all local commits with `--fold``.
    """
    actiondesc = ui.label(
        {
            b'created': _(b'created'),
            b'skipped': _(b'skipped'),
            b'updated': _(b'updated'),
        }[action],
        b'phabricator.action.%s' % action,
    )
    drevdesc = ui.label(b'D%d' % newrevid, b'phabricator.drev')
    summary = cmdutil.format_changeset_summary(ui, ctx, b'phabsend')
    ui.write(_(b'%s - %s - %s\n') % (drevdesc, actiondesc, summary))


def _amend_diff_properties(unfi, drevid, newnodes, diff):
    """update the local commit list for the ``diff`` associated with ``drevid``

    This is a utility function for the amend phase of ``phabsend``, which
    converts failures to warning messages.
    """
    _debug(
        unfi.ui,
        b"new commits: %s\n" % stringutil.pprint([short(n) for n in newnodes]),
    )

    try:
        writediffproperties([unfi[newnode] for newnode in newnodes], diff)
    except util.urlerr.urlerror:
        # If it fails just warn and keep going, otherwise the DREV
        # associations will be lost
        unfi.ui.warnnoi18n(b'Failed to update metadata for D%d\n' % drevid)


@vcrcommand(
    b'phabsend',
    [
        (b'r', b'rev', [], _(b'revisions to send'), _(b'REV')),
        (b'', b'amend', True, _(b'update commit messages')),
        (b'', b'reviewer', [], _(b'specify reviewers')),
        (b'', b'blocker', [], _(b'specify blocking reviewers')),
        (
            b'm',
            b'comment',
            b'',
            _(b'add a comment to Revisions with new/updated Diffs'),
        ),
        (b'', b'confirm', None, _(b'ask for confirmation before sending')),
        (b'', b'fold', False, _(b'combine the revisions into one review')),
    ],
    _(b'REV [OPTIONS]'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def phabsend(ui, repo, *revs, **opts):
    """upload changesets to Phabricator

    If there are multiple revisions specified, they will be send as a stack
    with a linear dependencies relationship using the order specified by the
    revset.

    For the first time uploading changesets, local tags will be created to
    maintain the association. After the first time, phabsend will check
    obsstore and tags information so it can figure out whether to update an
    existing Differential Revision, or create a new one.

    If --amend is set, update commit messages so they have the
    ``Differential Revision`` URL, remove related tags. This is similar to what
    arcanist will do, and is more desired in author-push workflows. Otherwise,
    use local tags to record the ``Differential Revision`` association.

    The --confirm option lets you confirm changesets before sending them. You
    can also add following to your configuration file to make it default
    behaviour::

        [phabsend]
        confirm = true

    By default, a separate review will be created for each commit that is
    selected, and will have the same parent/child relationship in Phabricator.
    If ``--fold`` is set, multiple commits are rolled up into a single review
    as if diffed from the parent of the first revision to the last.  The commit
    messages are concatenated in the summary field on Phabricator.

    phabsend will check obsstore and the above association to decide whether to
    update an existing Differential Revision, or create a new one.
    """
    opts = pycompat.byteskwargs(opts)
    revs = list(revs) + opts.get(b'rev', [])
    revs = logcmdutil.revrange(repo, revs)
    revs.sort()  # ascending order to preserve topological parent/child in phab

    if not revs:
        raise error.Abort(_(b'phabsend requires at least one changeset'))
    if opts.get(b'amend'):
        cmdutil.checkunfinished(repo)

    ctxs = [repo[rev] for rev in revs]

    if any(c for c in ctxs if c.obsolete()):
        raise error.Abort(_(b"obsolete commits cannot be posted for review"))

    # Ensure the local commits are an unbroken range.  The semantics of the
    # --fold option implies this, and the auto restacking of orphans requires
    # it.  Otherwise A+C in A->B->C will cause B to be orphaned, and C' to
    # get A' as a parent.
    def _fail_nonlinear_revs(revs, revtype):
        badnodes = [repo[r].node() for r in revs]
        raise error.Abort(
            _(b"cannot phabsend multiple %s revisions: %s")
            % (revtype, scmutil.nodesummaries(repo, badnodes)),
            hint=_(b"the revisions must form a linear chain"),
        )

    heads = repo.revs(b'heads(%ld)', revs)
    if len(heads) > 1:
        _fail_nonlinear_revs(heads, b"head")

    roots = repo.revs(b'roots(%ld)', revs)
    if len(roots) > 1:
        _fail_nonlinear_revs(roots, b"root")

    fold = opts.get(b'fold')
    if fold:
        if len(revs) == 1:
            # TODO: just switch to --no-fold instead?
            raise error.Abort(_(b"cannot fold a single revision"))

        # There's no clear way to manage multiple commits with a Dxxx tag, so
        # require the amend option.  (We could append "_nnn", but then it
        # becomes jumbled if earlier commits are added to an update.)  It should
        # lock the repo and ensure that the range is editable, but that would
        # make the code pretty convoluted.  The default behavior of `arc` is to
        # create a new review anyway.
        if not opts.get(b"amend"):
            raise error.Abort(_(b"cannot fold with --no-amend"))

        # It might be possible to bucketize the revisions by the DREV value, and
        # iterate over those groups when posting, and then again when amending.
        # But for simplicity, require all selected revisions to be for the same
        # DREV (if present).  Adding local revisions to an existing DREV is
        # acceptable.
        drevmatchers = [
            _differentialrevisiondescre.search(ctx.description())
            for ctx in ctxs
        ]
        if len({m.group('url') for m in drevmatchers if m}) > 1:
            raise error.Abort(
                _(b"cannot fold revisions with different DREV values")
            )

    # {newnode: (oldnode, olddiff, olddrev}
    oldmap = getoldnodedrevmap(repo, [repo[r].node() for r in revs])

    confirm = ui.configbool(b'phabsend', b'confirm')
    confirm |= bool(opts.get(b'confirm'))
    if confirm:
        confirmed = _confirmbeforesend(repo, revs, oldmap)
        if not confirmed:
            raise error.Abort(_(b'phabsend cancelled'))

    actions = []
    reviewers = opts.get(b'reviewer', [])
    blockers = opts.get(b'blocker', [])
    phids = []
    if reviewers:
        phids.extend(userphids(repo.ui, reviewers))
    if blockers:
        phids.extend(
            map(
                lambda phid: b'blocking(%s)' % phid,
                userphids(repo.ui, blockers),
            )
        )
    if phids:
        actions.append({b'type': b'reviewers.add', b'value': phids})

    drevids = []  # [int]
    diffmap = {}  # {newnode: diff}

    # Send patches one by one so we know their Differential Revision PHIDs and
    # can provide dependency relationship
    lastrevphid = None
    for ctx in ctxs:
        if fold:
            ui.debug(b'sending rev %d::%d\n' % (ctx.rev(), ctxs[-1].rev()))
        else:
            ui.debug(b'sending rev %d\n' % ctx.rev())

        # Get Differential Revision ID
        oldnode, olddiff, revid = oldmap.get(ctx.node(), (None, None, None))
        oldbasenode, oldbasediff, oldbaserevid = oldnode, olddiff, revid

        if fold:
            oldbasenode, oldbasediff, oldbaserevid = oldmap.get(
                ctxs[-1].node(), (None, None, None)
            )

        if oldnode != ctx.node() or opts.get(b'amend'):
            # Create or update Differential Revision
            revision, diff = createdifferentialrevision(
                ctxs if fold else [ctx],
                revid,
                lastrevphid,
                oldbasenode,
                oldnode,
                olddiff,
                actions,
                opts.get(b'comment'),
            )

            if fold:
                for ctx in ctxs:
                    diffmap[ctx.node()] = diff
            else:
                diffmap[ctx.node()] = diff

            newrevid = int(revision[b'object'][b'id'])
            newrevphid = revision[b'object'][b'phid']
            if revid:
                action = b'updated'
            else:
                action = b'created'

            # Create a local tag to note the association, if commit message
            # does not have it already
            if not fold:
                m = _differentialrevisiondescre.search(ctx.description())
                if not m or int(m.group('id')) != newrevid:
                    tagname = b'D%d' % newrevid
                    tags.tag(
                        repo,
                        tagname,
                        ctx.node(),
                        message=None,
                        user=None,
                        date=None,
                        local=True,
                    )
        else:
            # Nothing changed. But still set "newrevphid" so the next revision
            # could depend on this one and "newrevid" for the summary line.
            newrevphid = querydrev(repo.ui, b'%d' % revid)[0][b'phid']
            newrevid = revid
            action = b'skipped'

        drevids.append(newrevid)
        lastrevphid = newrevphid

        if fold:
            for c in ctxs:
                if oldmap.get(c.node(), (None, None, None))[2]:
                    action = b'updated'
                else:
                    action = b'created'
                _print_phabsend_action(ui, c, newrevid, action)
            break

        _print_phabsend_action(ui, ctx, newrevid, action)

    # Update commit messages and remove tags
    if opts.get(b'amend'):
        unfi = repo.unfiltered()
        drevs = callconduit(ui, b'differential.query', {b'ids': drevids})
        with repo.wlock(), repo.lock(), repo.transaction(b'phabsend'):
            # Eagerly evaluate commits to restabilize before creating new
            # commits.  The selected revisions are excluded because they are
            # automatically restacked as part of the submission process.
            restack = [
                c
                for c in repo.set(
                    b"(%ld::) - (%ld) - unstable() - obsolete() - public()",
                    revs,
                    revs,
                )
            ]
            wnode = unfi[b'.'].node()
            mapping = {}  # {oldnode: [newnode]}
            newnodes = []

            drevid = drevids[0]

            for i, rev in enumerate(revs):
                old = unfi[rev]
                if not fold:
                    drevid = drevids[i]
                drev = [d for d in drevs if int(d[b'id']) == drevid][0]

                newdesc = get_amended_desc(drev, old, fold)
                # Make sure commit message contain "Differential Revision"
                if (
                    old.description() != newdesc
                    or old.p1().node() in mapping
                    or old.p2().node() in mapping
                ):
                    if old.phase() == phases.public:
                        ui.warn(
                            _(b"warning: not updating public commit %s\n")
                            % scmutil.formatchangeid(old)
                        )
                        continue
                    parents = [
                        mapping.get(old.p1().node(), (old.p1(),))[0],
                        mapping.get(old.p2().node(), (old.p2(),))[0],
                    ]
                    newdesc = rewriteutil.update_hash_refs(
                        repo,
                        newdesc,
                        mapping,
                    )
                    new = context.metadataonlyctx(
                        repo,
                        old,
                        parents=parents,
                        text=newdesc,
                        user=old.user(),
                        date=old.date(),
                        extra=old.extra(),
                    )

                    newnode = new.commit()

                    mapping[old.node()] = [newnode]

                    if fold:
                        # Defer updating the (single) Diff until all nodes are
                        # collected.  No tags were created, so none need to be
                        # removed.
                        newnodes.append(newnode)
                        continue

                    _amend_diff_properties(
                        unfi, drevid, [newnode], diffmap[old.node()]
                    )

                    # Remove local tags since it's no longer necessary
                    tagname = b'D%d' % drevid
                    if tagname in repo.tags():
                        tags.tag(
                            repo,
                            tagname,
                            repo.nullid,
                            message=None,
                            user=None,
                            date=None,
                            local=True,
                        )
                elif fold:
                    # When folding multiple commits into one review with
                    # --fold, track even the commits that weren't amended, so
                    # that their association isn't lost if the properties are
                    # rewritten below.
                    newnodes.append(old.node())

            # If the submitted commits are public, no amend takes place so
            # there are no newnodes and therefore no diff update to do.
            if fold and newnodes:
                diff = diffmap[old.node()]

                # The diff object in diffmap doesn't have the local commits
                # because that could be returned from differential.creatediff,
                # not differential.querydiffs.  So use the queried diff (if
                # present), or force the amend (a new revision is being posted.)
                if not olddiff or set(newnodes) != getlocalcommits(olddiff):
                    _debug(ui, b"updating local commit list for D%d\n" % drevid)
                    _amend_diff_properties(unfi, drevid, newnodes, diff)
                else:
                    _debug(
                        ui,
                        b"local commit list for D%d is already up-to-date\n"
                        % drevid,
                    )
            elif fold:
                _debug(ui, b"no newnodes to update\n")

            # Restack any children of first-time submissions that were orphaned
            # in the process.  The ctx won't report that it is an orphan until
            # the cleanup takes place below.
            for old in restack:
                parents = [
                    mapping.get(old.p1().node(), (old.p1(),))[0],
                    mapping.get(old.p2().node(), (old.p2(),))[0],
                ]
                new = context.metadataonlyctx(
                    repo,
                    old,
                    parents=parents,
                    text=rewriteutil.update_hash_refs(
                        repo, old.description(), mapping
                    ),
                    user=old.user(),
                    date=old.date(),
                    extra=old.extra(),
                )

                newnode = new.commit()

                # Don't obsolete unselected descendants of nodes that have not
                # been changed in this transaction- that results in an error.
                if newnode != old.node():
                    mapping[old.node()] = [newnode]
                    _debug(
                        ui,
                        b"restabilizing %s as %s\n"
                        % (short(old.node()), short(newnode)),
                    )
                else:
                    _debug(
                        ui,
                        b"not restabilizing unchanged %s\n" % short(old.node()),
                    )

            scmutil.cleanupnodes(repo, mapping, b'phabsend', fixphase=True)
            if wnode in mapping:
                unfi.setparents(mapping[wnode][0])


# Map from "hg:meta" keys to header understood by "hg import". The order is
# consistent with "hg export" output.
_metanamemap = util.sortdict(
    [
        (b'user', b'User'),
        (b'date', b'Date'),
        (b'branch', b'Branch'),
        (b'node', b'Node ID'),
        (b'parent', b'Parent '),
    ]
)


def _confirmbeforesend(repo, revs, oldmap):
    url, token = readurltoken(repo.ui)
    ui = repo.ui
    for rev in revs:
        ctx = repo[rev]
        oldnode, olddiff, drevid = oldmap.get(ctx.node(), (None, None, None))
        if drevid:
            drevdesc = ui.label(b'D%d' % drevid, b'phabricator.drev')
        else:
            drevdesc = ui.label(_(b'NEW'), b'phabricator.drev')

        ui.write(
            _(b'%s - %s\n')
            % (
                drevdesc,
                cmdutil.format_changeset_summary(ui, ctx, b'phabsend'),
            )
        )

    if ui.promptchoice(
        _(b'Send the above changes to %s (Y/n)?$$ &Yes $$ &No') % url
    ):
        return False

    return True


_knownstatusnames = {
    b'accepted',
    b'needsreview',
    b'needsrevision',
    b'closed',
    b'abandoned',
    b'changesplanned',
}


def _getstatusname(drev):
    """get normalized status name from a Differential Revision"""
    return drev[b'statusName'].replace(b' ', b'').lower()


# Small language to specify differential revisions. Support symbols: (), :X,
# +, and -.

_elements = {
    # token-type: binding-strength, primary, prefix, infix, suffix
    b'(': (12, None, (b'group', 1, b')'), None, None),
    b':': (8, None, (b'ancestors', 8), None, None),
    b'&': (5, None, None, (b'and_', 5), None),
    b'+': (4, None, None, (b'add', 4), None),
    b'-': (4, None, None, (b'sub', 4), None),
    b')': (0, None, None, None, None),
    b'symbol': (0, b'symbol', None, None, None),
    b'end': (0, None, None, None, None),
}


def _tokenize(text):
    view = memoryview(text)  # zero-copy slice
    special = b'():+-& '
    pos = 0
    length = len(text)
    while pos < length:
        symbol = b''.join(
            itertools.takewhile(
                lambda ch: ch not in special, pycompat.iterbytestr(view[pos:])
            )
        )
        if symbol:
            yield (b'symbol', symbol, pos)
            pos += len(symbol)
        else:  # special char, ignore space
            if text[pos : pos + 1] != b' ':
                yield (text[pos : pos + 1], None, pos)
            pos += 1
    yield (b'end', None, pos)


def _parse(text):
    tree, pos = parser.parser(_elements).parse(_tokenize(text))
    if pos != len(text):
        raise error.ParseError(b'invalid token', pos)
    return tree


def _parsedrev(symbol):
    """str -> int or None, ex. 'D45' -> 45; '12' -> 12; 'x' -> None"""
    if symbol.startswith(b'D') and symbol[1:].isdigit():
        return int(symbol[1:])
    if symbol.isdigit():
        return int(symbol)


def _prefetchdrevs(tree):
    """return ({single-drev-id}, {ancestor-drev-id}) to prefetch"""
    drevs = set()
    ancestordrevs = set()
    op = tree[0]
    if op == b'symbol':
        r = _parsedrev(tree[1])
        if r:
            drevs.add(r)
    elif op == b'ancestors':
        r, a = _prefetchdrevs(tree[1])
        drevs.update(r)
        ancestordrevs.update(r)
        ancestordrevs.update(a)
    else:
        for t in tree[1:]:
            r, a = _prefetchdrevs(t)
            drevs.update(r)
            ancestordrevs.update(a)
    return drevs, ancestordrevs


def querydrev(ui, spec):
    """return a list of "Differential Revision" dicts

    spec is a string using a simple query language, see docstring in phabread
    for details.

    A "Differential Revision dict" looks like:

        {
            "activeDiffPHID": "PHID-DIFF-xoqnjkobbm6k4dk6hi72",
            "authorPHID": "PHID-USER-tv3ohwc4v4jeu34otlye",
            "auxiliary": {
              "phabricator:depends-on": [
                "PHID-DREV-gbapp366kutjebt7agcd"
              ]
              "phabricator:projects": [],
            },
            "branch": "default",
            "ccs": [],
            "commits": [],
            "dateCreated": "1499181406",
            "dateModified": "1499182103",
            "diffs": [
              "3",
              "4",
            ],
            "hashes": [],
            "id": "2",
            "lineCount": "2",
            "phid": "PHID-DREV-672qvysjcczopag46qty",
            "properties": {},
            "repositoryPHID": "PHID-REPO-hub2hx62ieuqeheznasv",
            "reviewers": [],
            "sourcePath": null
            "status": "0",
            "statusName": "Needs Review",
            "summary": "",
            "testPlan": "",
            "title": "example",
            "uri": "https://phab.example.com/D2",
        }
    """
    # TODO: replace differential.query and differential.querydiffs with
    # differential.diff.search because the former (and their output) are
    # frozen, and planned to be deprecated and removed.

    def fetch(params):
        """params -> single drev or None"""
        key = (params.get(b'ids') or params.get(b'phids') or [None])[0]
        if key in prefetched:
            return prefetched[key]
        drevs = callconduit(ui, b'differential.query', params)
        # Fill prefetched with the result
        for drev in drevs:
            prefetched[drev[b'phid']] = drev
            prefetched[int(drev[b'id'])] = drev
        if key not in prefetched:
            raise error.Abort(
                _(b'cannot get Differential Revision %r') % params
            )
        return prefetched[key]

    def getstack(topdrevids):
        """given a top, get a stack from the bottom, [id] -> [id]"""
        visited = set()
        result = []
        queue = [{b'ids': [i]} for i in topdrevids]
        while queue:
            params = queue.pop()
            drev = fetch(params)
            if drev[b'id'] in visited:
                continue
            visited.add(drev[b'id'])
            result.append(int(drev[b'id']))
            auxiliary = drev.get(b'auxiliary', {})
            depends = auxiliary.get(b'phabricator:depends-on', [])
            for phid in depends:
                queue.append({b'phids': [phid]})
        result.reverse()
        return smartset.baseset(result)

    # Initialize prefetch cache
    prefetched = {}  # {id or phid: drev}

    tree = _parse(spec)
    drevs, ancestordrevs = _prefetchdrevs(tree)

    # developer config: phabricator.batchsize
    batchsize = ui.configint(b'phabricator', b'batchsize')

    # Prefetch Differential Revisions in batch
    tofetch = set(drevs)
    for r in ancestordrevs:
        tofetch.update(range(max(1, r - batchsize), r + 1))
    if drevs:
        fetch({b'ids': list(tofetch)})
    validids = sorted(set(getstack(list(ancestordrevs))) | set(drevs))

    # Walk through the tree, return smartsets
    def walk(tree):
        op = tree[0]
        if op == b'symbol':
            drev = _parsedrev(tree[1])
            if drev:
                return smartset.baseset([drev])
            elif tree[1] in _knownstatusnames:
                drevs = [
                    r
                    for r in validids
                    if _getstatusname(prefetched[r]) == tree[1]
                ]
                return smartset.baseset(drevs)
            else:
                raise error.Abort(_(b'unknown symbol: %s') % tree[1])
        elif op in {b'and_', b'add', b'sub'}:
            assert len(tree) == 3
            return getattr(operator, pycompat.sysstr(op))(
                walk(tree[1]), walk(tree[2])
            )
        elif op == b'group':
            return walk(tree[1])
        elif op == b'ancestors':
            return getstack(walk(tree[1]))
        else:
            raise error.ProgrammingError(b'illegal tree: %r' % tree)

    return [prefetched[r] for r in walk(tree)]


def getdescfromdrev(drev):
    """get description (commit message) from "Differential Revision"

    This is similar to differential.getcommitmessage API. But we only care
    about limited fields: title, summary, test plan, and URL.
    """
    title = drev[b'title']
    summary = drev[b'summary'].rstrip()
    testplan = drev[b'testPlan'].rstrip()
    if testplan:
        testplan = b'Test Plan:\n%s' % testplan
    uri = b'Differential Revision: %s' % drev[b'uri']
    return b'\n\n'.join(filter(None, [title, summary, testplan, uri]))


def get_amended_desc(drev, ctx, folded):
    """similar to ``getdescfromdrev``, but supports a folded series of commits

    This is used when determining if an individual commit needs to have its
    message amended after posting it for review.  The determination is made for
    each individual commit, even when they were folded into one review.
    """
    if not folded:
        return getdescfromdrev(drev)

    uri = b'Differential Revision: %s' % drev[b'uri']

    # Since the commit messages were combined when posting multiple commits
    # with --fold, the fields can't be read from Phabricator here, or *all*
    # affected local revisions will end up with the same commit message after
    # the URI is amended in.  Append in the DREV line, or update it if it
    # exists.  At worst, this means commit message or test plan updates on
    # Phabricator aren't propagated back to the repository, but that seems
    # reasonable for the case where local commits are effectively combined
    # in Phabricator.
    m = _differentialrevisiondescre.search(ctx.description())
    if not m:
        return b'\n\n'.join([ctx.description(), uri])

    return _differentialrevisiondescre.sub(uri, ctx.description())


def getlocalcommits(diff):
    """get the set of local commits from a diff object

    See ``getdiffmeta()`` for an example diff object.
    """
    props = diff.get(b'properties') or {}
    commits = props.get(b'local:commits') or {}
    if len(commits) > 1:
        return {bin(c) for c in commits.keys()}

    # Storing the diff metadata predates storing `local:commits`, so continue
    # to use that in the --no-fold case.
    return {bin(getdiffmeta(diff).get(b'node', b'')) or None}


def getdiffmeta(diff):
    """get commit metadata (date, node, user, p1) from a diff object

    The metadata could be "hg:meta", sent by phabsend, like:

        "properties": {
          "hg:meta": {
            "branch": "default",
            "date": "1499571514 25200",
            "node": "98c08acae292b2faf60a279b4189beb6cff1414d",
            "user": "Foo Bar <foo@example.com>",
            "parent": "6d0abad76b30e4724a37ab8721d630394070fe16"
          }
        }

    Or converted from "local:commits", sent by "arc", like:

        "properties": {
          "local:commits": {
            "98c08acae292b2faf60a279b4189beb6cff1414d": {
              "author": "Foo Bar",
              "authorEmail": "foo@example.com"
              "branch": "default",
              "commit": "98c08acae292b2faf60a279b4189beb6cff1414d",
              "local": "1000",
              "message": "...",
              "parents": ["6d0abad76b30e4724a37ab8721d630394070fe16"],
              "rev": "98c08acae292b2faf60a279b4189beb6cff1414d",
              "summary": "...",
              "tag": "",
              "time": 1499546314,
            }
          }
        }

    Note: metadata extracted from "local:commits" will lose time zone
    information.
    """
    props = diff.get(b'properties') or {}
    meta = props.get(b'hg:meta')
    if not meta:
        if props.get(b'local:commits'):
            commit = sorted(props[b'local:commits'].values())[0]
            meta = {}
            if b'author' in commit and b'authorEmail' in commit:
                meta[b'user'] = b'%s <%s>' % (
                    commit[b'author'],
                    commit[b'authorEmail'],
                )
            if b'time' in commit:
                meta[b'date'] = b'%d 0' % int(commit[b'time'])
            if b'branch' in commit:
                meta[b'branch'] = commit[b'branch']
            node = commit.get(b'commit', commit.get(b'rev'))
            if node:
                meta[b'node'] = node
            if len(commit.get(b'parents', ())) >= 1:
                meta[b'parent'] = commit[b'parents'][0]
        else:
            meta = {}
    if b'date' not in meta and b'dateCreated' in diff:
        meta[b'date'] = b'%s 0' % diff[b'dateCreated']
    if b'branch' not in meta and diff.get(b'branch'):
        meta[b'branch'] = diff[b'branch']
    if b'parent' not in meta and diff.get(b'sourceControlBaseRevision'):
        meta[b'parent'] = diff[b'sourceControlBaseRevision']
    return meta


def _getdrevs(ui, stack, specs):
    """convert user supplied DREVSPECs into "Differential Revision" dicts

    See ``hg help phabread`` for how to specify each DREVSPEC.
    """
    if len(specs) > 0:

        def _formatspec(s):
            if stack:
                s = b':(%s)' % s
            return b'(%s)' % s

        spec = b'+'.join(pycompat.maplist(_formatspec, specs))

        drevs = querydrev(ui, spec)
        if drevs:
            return drevs

    raise error.Abort(_(b"empty DREVSPEC set"))


def readpatch(ui, drevs, write):
    """generate plain-text patch readable by 'hg import'

    write takes a list of (DREV, bytes), where DREV is the differential number
    (as bytes, without the "D" prefix) and the bytes are the text of a patch
    to be imported. drevs is what "querydrev" returns, results of
    "differential.query".
    """
    # Prefetch hg:meta property for all diffs
    diffids = sorted({max(int(v) for v in drev[b'diffs']) for drev in drevs})
    diffs = callconduit(ui, b'differential.querydiffs', {b'ids': diffids})

    patches = []

    # Generate patch for each drev
    for drev in drevs:
        ui.note(_(b'reading D%s\n') % drev[b'id'])

        diffid = max(int(v) for v in drev[b'diffs'])
        body = callconduit(ui, b'differential.getrawdiff', {b'diffID': diffid})
        desc = getdescfromdrev(drev)
        header = b'# HG changeset patch\n'

        # Try to preserve metadata from hg:meta property. Write hg patch
        # headers that can be read by the "import" command. See patchheadermap
        # and extract in mercurial/patch.py for supported headers.
        meta = getdiffmeta(diffs[b'%d' % diffid])
        for k in _metanamemap.keys():
            if k in meta:
                header += b'# %s %s\n' % (_metanamemap[k], meta[k])

        content = b'%s%s\n%s' % (header, desc, body)
        patches.append((drev[b'id'], content))

    # Write patches to the supplied callback
    write(patches)


@vcrcommand(
    b'phabread',
    [(b'', b'stack', False, _(b'read dependencies'))],
    _(b'DREVSPEC... [OPTIONS]'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
    optionalrepo=True,
)
def phabread(ui, repo, *specs, **opts):
    """print patches from Phabricator suitable for importing

    DREVSPEC could be a Differential Revision identity, like ``D123``, or just
    the number ``123``. It could also have common operators like ``+``, ``-``,
    ``&``, ``(``, ``)`` for complex queries. Prefix ``:`` could be used to
    select a stack.  If multiple DREVSPEC values are given, the result is the
    union of each individually evaluated value.  No attempt is currently made
    to reorder the values to run from parent to child.

    ``abandoned``, ``accepted``, ``closed``, ``needsreview``, ``needsrevision``
    could be used to filter patches by status. For performance reason, they
    only represent a subset of non-status selections and cannot be used alone.

    For example, ``:D6+8-(2+D4)`` selects a stack up to D6, plus D8 and exclude
    D2 and D4. ``:D9 & needsreview`` selects "Needs Review" revisions in a
    stack up to D9.

    If --stack is given, follow dependencies information and read all patches.
    It is equivalent to the ``:`` operator.
    """
    opts = pycompat.byteskwargs(opts)
    drevs = _getdrevs(ui, opts.get(b'stack'), specs)

    def _write(patches):
        for drev, content in patches:
            ui.write(content)

    readpatch(ui, drevs, _write)


@vcrcommand(
    b'phabimport',
    [(b'', b'stack', False, _(b'import dependencies as well'))],
    _(b'DREVSPEC... [OPTIONS]'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def phabimport(ui, repo, *specs, **opts):
    """import patches from Phabricator for the specified Differential Revisions

    The patches are read and applied starting at the parent of the working
    directory.

    See ``hg help phabread`` for how to specify DREVSPEC.
    """
    opts = pycompat.byteskwargs(opts)

    # --bypass avoids losing exec and symlink bits when importing on Windows,
    # and allows importing with a dirty wdir.  It also aborts instead of leaving
    # rejects.
    opts[b'bypass'] = True

    # Mandatory default values, synced with commands.import
    opts[b'strip'] = 1
    opts[b'prefix'] = b''
    # Evolve 9.3.0 assumes this key is present in cmdutil.tryimportone()
    opts[b'obsolete'] = False

    if ui.configbool(b'phabimport', b'secret'):
        opts[b'secret'] = True
    if ui.configbool(b'phabimport', b'obsolete'):
        opts[b'obsolete'] = True  # Handled by evolve wrapping tryimportone()

    def _write(patches):
        parents = repo[None].parents()

        with repo.wlock(), repo.lock(), repo.transaction(b'phabimport'):
            for drev, contents in patches:
                ui.status(_(b'applying patch from D%s\n') % drev)

                with patch.extract(ui, io.BytesIO(contents)) as patchdata:
                    msg, node, rej = cmdutil.tryimportone(
                        ui,
                        repo,
                        patchdata,
                        parents,
                        opts,
                        [],
                        None,  # Never update wdir to another revision
                    )

                    if not node:
                        raise error.Abort(_(b'D%s: no diffs found') % drev)

                    ui.note(msg + b'\n')
                    parents = [repo[node]]

    drevs = _getdrevs(ui, opts.get(b'stack'), specs)

    readpatch(repo.ui, drevs, _write)


@vcrcommand(
    b'phabupdate',
    [
        (b'', b'accept', False, _(b'accept revisions')),
        (b'', b'reject', False, _(b'reject revisions')),
        (b'', b'request-review', False, _(b'request review on revisions')),
        (b'', b'abandon', False, _(b'abandon revisions')),
        (b'', b'reclaim', False, _(b'reclaim revisions')),
        (b'', b'close', False, _(b'close revisions')),
        (b'', b'reopen', False, _(b'reopen revisions')),
        (b'', b'plan-changes', False, _(b'plan changes for revisions')),
        (b'', b'resign', False, _(b'resign as a reviewer from revisions')),
        (b'', b'commandeer', False, _(b'commandeer revisions')),
        (b'm', b'comment', b'', _(b'comment on the last revision')),
        (b'r', b'rev', b'', _(b'local revision to update'), _(b'REV')),
    ],
    _(b'[DREVSPEC...| -r REV...] [OPTIONS]'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
    optionalrepo=True,
)
def phabupdate(ui, repo, *specs, **opts):
    """update Differential Revision in batch

    DREVSPEC selects revisions. See :hg:`help phabread` for its usage.
    """
    opts = pycompat.byteskwargs(opts)
    transactions = [
        b'abandon',
        b'accept',
        b'close',
        b'commandeer',
        b'plan-changes',
        b'reclaim',
        b'reject',
        b'reopen',
        b'request-review',
        b'resign',
    ]
    flags = [n for n in transactions if opts.get(n.replace(b'-', b'_'))]
    if len(flags) > 1:
        raise error.Abort(_(b'%s cannot be used together') % b', '.join(flags))

    actions = []
    for f in flags:
        actions.append({b'type': f, b'value': True})

    revs = opts.get(b'rev')
    if revs:
        if not repo:
            raise error.InputError(_(b'--rev requires a repository'))

        if specs:
            raise error.InputError(_(b'cannot specify both DREVSPEC and --rev'))

        drevmap = getdrevmap(repo, logcmdutil.revrange(repo, [revs]))
        specs = []
        unknown = []
        for r, d in drevmap.items():
            if d is None:
                unknown.append(repo[r])
            else:
                specs.append(b'D%d' % d)
        if unknown:
            raise error.InputError(
                _(b'selected revisions without a Differential: %s')
                % scmutil.nodesummaries(repo, unknown)
            )

    drevs = _getdrevs(ui, opts.get(b'stack'), specs)
    for i, drev in enumerate(drevs):
        if i + 1 == len(drevs) and opts.get(b'comment'):
            actions.append({b'type': b'comment', b'value': opts[b'comment']})
        if actions:
            params = {
                b'objectIdentifier': drev[b'phid'],
                b'transactions': actions,
            }
            callconduit(ui, b'differential.revision.edit', params)


@eh.templatekeyword(b'phabreview', requires={b'ctx'})
def template_review(context, mapping):
    """:phabreview: Object describing the review for this changeset.
    Has attributes `url` and `id`.
    """
    ctx = context.resource(mapping, b'ctx')
    m = _differentialrevisiondescre.search(ctx.description())
    if m:
        return templateutil.hybriddict(
            {
                b'url': m.group('url'),
                b'id': b"D%s" % m.group('id'),
            }
        )
    else:
        tags = ctx.repo().nodetags(ctx.node())
        for t in tags:
            if _differentialrevisiontagre.match(t):
                url = ctx.repo().ui.config(b'phabricator', b'url')
                if not url.endswith(b'/'):
                    url += b'/'
                url += t

                return templateutil.hybriddict(
                    {
                        b'url': url,
                        b'id': t,
                    }
                )
    return None


@eh.templatekeyword(b'phabstatus', requires={b'ctx', b'repo', b'ui'})
def template_status(context, mapping):
    """:phabstatus: String. Status of Phabricator differential."""
    ctx = context.resource(mapping, b'ctx')
    repo = context.resource(mapping, b'repo')
    ui = context.resource(mapping, b'ui')

    rev = ctx.rev()
    try:
        drevid = getdrevmap(repo, [rev])[rev]
    except KeyError:
        return None
    drevs = callconduit(ui, b'differential.query', {b'ids': [drevid]})
    for drev in drevs:
        if int(drev[b'id']) == drevid:
            return templateutil.hybriddict(
                {
                    b'url': drev[b'uri'],
                    b'status': drev[b'statusName'],
                }
            )
    return None


@show.showview(b'phabstatus', csettopic=b'work')
def phabstatusshowview(ui, repo, displayer):
    """Phabricator differiential status"""
    revs = repo.revs('sort(_underway(), topo)')
    drevmap = getdrevmap(repo, revs)
    unknownrevs, drevids, revsbydrevid = [], set(), {}
    for rev, drevid in drevmap.items():
        if drevid is not None:
            drevids.add(drevid)
            revsbydrevid.setdefault(drevid, set()).add(rev)
        else:
            unknownrevs.append(rev)

    drevs = callconduit(ui, b'differential.query', {b'ids': list(drevids)})
    drevsbyrev = {}
    for drev in drevs:
        for rev in revsbydrevid[int(drev[b'id'])]:
            drevsbyrev[rev] = drev

    def phabstatus(ctx):
        drev = drevsbyrev[ctx.rev()]
        status = ui.label(
            b'%(statusName)s' % drev,
            b'phabricator.status.%s' % _getstatusname(drev),
        )
        ui.write(b"\n%s %s\n" % (drev[b'uri'], status))

    revs -= smartset.baseset(unknownrevs)
    revdag = graphmod.dagwalker(repo, revs)

    ui.setconfig(b'experimental', b'graphshorten', True)
    displayer._exthook = phabstatus
    nodelen = show.longestshortest(repo, revs)
    logcmdutil.displaygraph(
        ui,
        repo,
        revdag,
        displayer,
        graphmod.asciiedges,
        props={b'nodelen': nodelen},
    )
