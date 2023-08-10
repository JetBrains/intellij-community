# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''track large binary files

Large binary files tend to be not very compressible, not very
diffable, and not at all mergeable. Such files are not handled
efficiently by Mercurial's storage format (revlog), which is based on
compressed binary deltas; storing large binary files as regular
Mercurial files wastes bandwidth and disk space and increases
Mercurial's memory usage. The largefiles extension addresses these
problems by adding a centralized client-server layer on top of
Mercurial: largefiles live in a *central store* out on the network
somewhere, and you only fetch the revisions that you need when you
need them.

largefiles works by maintaining a "standin file" in .hglf/ for each
largefile. The standins are small (41 bytes: an SHA-1 hash plus
newline) and are tracked by Mercurial. Largefile revisions are
identified by the SHA-1 hash of their contents, which is written to
the standin. largefiles uses that revision ID to get/put largefile
revisions from/to the central store. This saves both disk space and
bandwidth, since you don't need to retrieve all historical revisions
of large files when you clone or pull.

To start a new repository or add new large binary files, just add
--large to your :hg:`add` command. For example::

  $ dd if=/dev/urandom of=randomdata count=2000
  $ hg add --large randomdata
  $ hg commit -m "add randomdata as a largefile"

When you push a changeset that adds/modifies largefiles to a remote
repository, its largefile revisions will be uploaded along with it.
Note that the remote Mercurial must also have the largefiles extension
enabled for this to work.

When you pull a changeset that affects largefiles from a remote
repository, the largefiles for the changeset will by default not be
pulled down. However, when you update to such a revision, any
largefiles needed by that revision are downloaded and cached (if
they have never been downloaded before). One way to pull largefiles
when pulling is thus to use --update, which will update your working
copy to the latest pulled revision (and thereby downloading any new
largefiles).

If you want to pull largefiles you don't need for update yet, then
you can use pull with the `--lfrev` option or the :hg:`lfpull` command.

If you know you are pulling from a non-default location and want to
download all the largefiles that correspond to the new changesets at
the same time, then you can pull with `--lfrev "pulled()"`.

If you just want to ensure that you will have the largefiles needed to
merge or rebase with new heads that you are pulling, then you can pull
with `--lfrev "head(pulled())"` flag to pre-emptively download any largefiles
that are new in the heads you are pulling.

Keep in mind that network access may now be required to update to
changesets that you have not previously updated to. The nature of the
largefiles extension means that updating is no longer guaranteed to
be a local-only operation.

If you already have large files tracked by Mercurial without the
largefiles extension, you will need to convert your repository in
order to benefit from largefiles. This is done with the
:hg:`lfconvert` command::

  $ hg lfconvert --size 10 oldrepo newrepo

In repositories that already have largefiles in them, any new file
over 10MB will automatically be added as a largefile. To change this
threshold, set ``largefiles.minsize`` in your Mercurial config file
to the minimum size in megabytes to track as a largefile, or use the
--lfsize option to the add command (also in megabytes)::

  [largefiles]
  minsize = 2

  $ hg add --lfsize 2

The ``largefiles.patterns`` config option allows you to specify a list
of filename patterns (see :hg:`help patterns`) that should always be
tracked as largefiles::

  [largefiles]
  patterns =
    *.jpg
    re:.*\\.(png|bmp)$
    library.zip
    content/audio/*

Files that match one of these patterns will be added as largefiles
regardless of their size.

The ``largefiles.minsize`` and ``largefiles.patterns`` config options
will be ignored for any repositories not already containing a
largefile. To add the first largefile to a repository, you must
explicitly do so with the --large flag passed to the :hg:`add`
command.
'''
from __future__ import absolute_import

from mercurial import (
    cmdutil,
    extensions,
    exthelper,
    hg,
    localrepo,
    wireprotov1server,
)

from . import (
    lfcommands,
    overrides,
    proto,
    reposetup,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

eh = exthelper.exthelper()
eh.merge(lfcommands.eh)
eh.merge(overrides.eh)
eh.merge(proto.eh)

eh.configitem(
    b'largefiles',
    b'minsize',
    default=eh.configitem.dynamicdefault,
)
eh.configitem(
    b'largefiles',
    b'patterns',
    default=list,
)
eh.configitem(
    b'largefiles',
    b'usercache',
    default=None,
)

cmdtable = eh.cmdtable
configtable = eh.configtable
extsetup = eh.finalextsetup
reposetup = reposetup.reposetup
uisetup = eh.finaluisetup


def featuresetup(ui, supported):
    # don't die on seeing a repo with the largefiles requirement
    supported |= {b'largefiles'}


@eh.uisetup
def _uisetup(ui):
    localrepo.featuresetupfuncs.add(featuresetup)
    hg.wirepeersetupfuncs.append(proto.wirereposetup)

    cmdutil.outgoinghooks.add(b'largefiles', overrides.outgoinghook)
    cmdutil.summaryremotehooks.add(b'largefiles', overrides.summaryremotehook)

    # create the new wireproto commands ...
    wireprotov1server.wireprotocommand(b'putlfile', b'sha', permission=b'push')(
        proto.putlfile
    )
    wireprotov1server.wireprotocommand(b'getlfile', b'sha', permission=b'pull')(
        proto.getlfile
    )
    wireprotov1server.wireprotocommand(
        b'statlfile', b'sha', permission=b'pull'
    )(proto.statlfile)
    wireprotov1server.wireprotocommand(b'lheads', b'', permission=b'pull')(
        wireprotov1server.heads
    )

    extensions.wrapfunction(
        wireprotov1server.commands[b'heads'], b'func', proto.heads
    )
    # TODO also wrap wireproto.commandsv2 once heads is implemented there.

    # override some extensions' stuff as well
    for name, module in extensions.extensions():
        if name == b'rebase':
            # TODO: teach exthelper to handle this
            extensions.wrapfunction(
                module, b'rebase', overrides.overriderebasecmd
            )


revsetpredicate = eh.revsetpredicate
