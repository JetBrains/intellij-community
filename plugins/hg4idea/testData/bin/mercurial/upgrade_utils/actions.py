# upgrade.py - functions for in place upgrade of Mercurial repository
#
# Copyright (c) 2016-present, Gregory Szorc
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import random

from typing import (
    List,
    Type,
)

from ..i18n import _
from .. import (
    error,
    localrepo,
    requirements,
    revlog,
    util,
)

from ..utils import compression

# keeps pyflakes happy
assert [
    List,
    Type,
]

# list of requirements that request a clone of all revlog if added/removed
RECLONES_REQUIREMENTS = {
    requirements.GENERALDELTA_REQUIREMENT,
    requirements.SPARSEREVLOG_REQUIREMENT,
    requirements.REVLOGV2_REQUIREMENT,
    requirements.CHANGELOGV2_REQUIREMENT,
}


def preservedrequirements(repo):
    preserved = {
        requirements.SHARED_REQUIREMENT,
        requirements.NARROW_REQUIREMENT,
    }
    return preserved & repo.requirements


FORMAT_VARIANT = b'deficiency'
OPTIMISATION = b'optimization'


class improvement:
    """Represents an improvement that can be made as part of an upgrade."""

    ### The following attributes should be defined for each subclass:

    # Either ``FORMAT_VARIANT`` or ``OPTIMISATION``.
    # A format variant is where we change the storage format. Not all format
    # variant changes are an obvious problem.
    # An optimization is an action (sometimes optional) that
    # can be taken to further improve the state of the repository.
    type = None

    # machine-readable string uniquely identifying this improvement. it will be
    # mapped to an action later in the upgrade process.
    name = None

    # message intended for humans explaining the improvement in more detail,
    # including the implications of it ``FORMAT_VARIANT`` types, should be
    # worded
    # in the present tense.
    description = None

    # message intended for humans explaining what an upgrade addressing this
    # issue will do. should be worded in the future tense.
    upgrademessage = None

    # value of current Mercurial default for new repository
    default = None

    # Message intended for humans which will be shown post an upgrade
    # operation when the improvement will be added
    postupgrademessage = None

    # Message intended for humans which will be shown post an upgrade
    # operation in which this improvement was removed
    postdowngrademessage = None

    # By default we assume that every improvement touches requirements and all revlogs

    # Whether this improvement touches filelogs
    touches_filelogs = True

    # Whether this improvement touches manifests
    touches_manifests = True

    # Whether this improvement touches changelog
    touches_changelog = True

    # Whether this improvement changes repository requirements
    touches_requirements = True

    # Whether this improvement touches the dirstate
    touches_dirstate = False

    # Can this action be run on a share instead of its mains repository
    compatible_with_share = False


allformatvariant: List[Type['formatvariant']] = []


def registerformatvariant(cls):
    allformatvariant.append(cls)
    return cls


class formatvariant(improvement):
    """an improvement subclass dedicated to repository format"""

    type = FORMAT_VARIANT

    @staticmethod
    def fromrepo(repo):
        """current value of the variant in the repository"""
        raise NotImplementedError()

    @staticmethod
    def fromconfig(repo):
        """current value of the variant in the configuration"""
        raise NotImplementedError()


class requirementformatvariant(formatvariant):
    """formatvariant based on a 'requirement' name.

    Many format variant are controlled by a 'requirement'. We define a small
    subclass to factor the code.
    """

    # the requirement that control this format variant
    _requirement = None

    @staticmethod
    def _newreporequirements(ui):
        return localrepo.newreporequirements(
            ui, localrepo.defaultcreateopts(ui)
        )

    @classmethod
    def fromrepo(cls, repo):
        assert cls._requirement is not None
        return cls._requirement in repo.requirements

    @classmethod
    def fromconfig(cls, repo):
        assert cls._requirement is not None
        return cls._requirement in cls._newreporequirements(repo.ui)


@registerformatvariant
class fncache(requirementformatvariant):
    name = b'fncache'

    _requirement = requirements.FNCACHE_REQUIREMENT

    default = True

    description = _(
        b'long and reserved filenames may not work correctly; '
        b'repository performance is sub-optimal'
    )

    upgrademessage = _(
        b'repository will be more resilient to storing '
        b'certain paths and performance of certain '
        b'operations should be improved'
    )


@registerformatvariant
class dirstatev2(requirementformatvariant):
    name = b'dirstate-v2'
    _requirement = requirements.DIRSTATE_V2_REQUIREMENT

    default = False

    description = _(
        b'version 1 of the dirstate file format requires '
        b'reading and parsing it all at once.\n'
        b'Version 2 has a better structure,'
        b'better information and lighter update mechanism'
    )

    upgrademessage = _(b'"hg status" will be faster')

    touches_filelogs = False
    touches_manifests = False
    touches_changelog = False
    touches_requirements = True
    touches_dirstate = True
    compatible_with_share = True


@registerformatvariant
class dirstatetrackedkey(requirementformatvariant):
    name = b'tracked-hint'
    _requirement = requirements.DIRSTATE_TRACKED_HINT_V1

    default = False

    description = _(
        b'Add a small file to help external tooling that watch the tracked set'
    )

    upgrademessage = _(
        b'external tools will be informated of potential change in the tracked set'
    )

    touches_filelogs = False
    touches_manifests = False
    touches_changelog = False
    touches_requirements = True
    touches_dirstate = True
    compatible_with_share = True


@registerformatvariant
class dotencode(requirementformatvariant):
    name = b'dotencode'

    _requirement = requirements.DOTENCODE_REQUIREMENT

    default = True

    description = _(
        b'storage of filenames beginning with a period or '
        b'space may not work correctly'
    )

    upgrademessage = _(
        b'repository will be better able to store files '
        b'beginning with a space or period'
    )


@registerformatvariant
class generaldelta(requirementformatvariant):
    name = b'generaldelta'

    _requirement = requirements.GENERALDELTA_REQUIREMENT

    default = True

    description = _(
        b'deltas within internal storage are unable to '
        b'choose optimal revisions; repository is larger and '
        b'slower than it could be; interaction with other '
        b'repositories may require extra network and CPU '
        b'resources, making "hg push" and "hg pull" slower'
    )

    upgrademessage = _(
        b'repository storage will be able to create '
        b'optimal deltas; new repository data will be '
        b'smaller and read times should decrease; '
        b'interacting with other repositories using this '
        b'storage model should require less network and '
        b'CPU resources, making "hg push" and "hg pull" '
        b'faster'
    )


@registerformatvariant
class sharesafe(requirementformatvariant):
    name = b'share-safe'
    _requirement = requirements.SHARESAFE_REQUIREMENT

    default = True

    description = _(
        b'old shared repositories do not share source repository '
        b'requirements and config. This leads to various problems '
        b'when the source repository format is upgraded or some new '
        b'extensions are enabled.'
    )

    upgrademessage = _(
        b'Upgrades a repository to share-safe format so that future '
        b'shares of this repository share its requirements and configs.'
    )

    postdowngrademessage = _(
        b'repository downgraded to not use share safe mode, '
        b'existing shares will not work and need to be reshared.'
    )

    postupgrademessage = _(
        b'repository upgraded to share safe mode, existing'
        b' shares will still work in old non-safe mode. '
        b'Re-share existing shares to use them in safe mode'
        b' New shares will be created in safe mode.'
    )

    # upgrade only needs to change the requirements
    touches_filelogs = False
    touches_manifests = False
    touches_changelog = False
    touches_requirements = True


@registerformatvariant
class sparserevlog(requirementformatvariant):
    name = b'sparserevlog'

    _requirement = requirements.SPARSEREVLOG_REQUIREMENT

    default = True

    description = _(
        b'in order to limit disk reading and memory usage on older '
        b'version, the span of a delta chain from its root to its '
        b'end is limited, whatever the relevant data in this span. '
        b'This can severly limit Mercurial ability to build good '
        b'chain of delta resulting is much more storage space being '
        b'taken and limit reusability of on disk delta during '
        b'exchange.'
    )

    upgrademessage = _(
        b'Revlog supports delta chain with more unused data '
        b'between payload. These gaps will be skipped at read '
        b'time. This allows for better delta chains, making a '
        b'better compression and faster exchange with server.'
    )


@registerformatvariant
class persistentnodemap(requirementformatvariant):
    name = b'persistent-nodemap'

    _requirement = requirements.NODEMAP_REQUIREMENT

    default = False

    description = _(
        b'persist the node -> rev mapping on disk to speedup lookup'
    )

    upgrademessage = _(b'Speedup revision lookup by node id.')


@registerformatvariant
class copiessdc(requirementformatvariant):
    name = b'copies-sdc'

    _requirement = requirements.COPIESSDC_REQUIREMENT

    default = False

    description = _(b'Stores copies information alongside changesets.')

    upgrademessage = _(
        b'Allows to use more efficient algorithm to deal with copy tracing.'
    )

    touches_filelogs = False
    touches_manifests = False


@registerformatvariant
class revlogv2(requirementformatvariant):
    name = b'revlog-v2'
    _requirement = requirements.REVLOGV2_REQUIREMENT
    default = False
    description = _(b'Version 2 of the revlog.')
    upgrademessage = _(b'very experimental')


@registerformatvariant
class changelogv2(requirementformatvariant):
    name = b'changelog-v2'
    _requirement = requirements.CHANGELOGV2_REQUIREMENT
    default = False
    description = _(b'An iteration of the revlog focussed on changelog needs.')
    upgrademessage = _(b'quite experimental')

    touches_filelogs = False
    touches_manifests = False


@registerformatvariant
class removecldeltachain(formatvariant):
    name = b'plain-cl-delta'

    default = True

    description = _(
        b'changelog storage is using deltas instead of '
        b'raw entries; changelog reading and any '
        b'operation relying on changelog data are slower '
        b'than they could be'
    )

    upgrademessage = _(
        b'changelog storage will be reformated to '
        b'store raw entries; changelog reading will be '
        b'faster; changelog size may be reduced'
    )

    @staticmethod
    def fromrepo(repo):
        # Mercurial 4.0 changed changelogs to not use delta chains. Search for
        # changelogs with deltas.
        cl = repo.unfiltered().changelog
        if len(cl) <= 1000:
            some_rev = list(cl)
        else:
            # do a random sampling to speeds things up Scanning the whole
            # repository can get really slow on bigger repo.
            some_rev = sorted(
                {random.randint(0, len(cl) - 1) for x in range(1000)}
            )
        chainbase = cl.chainbase
        return all(rev == chainbase(rev) for rev in some_rev)

    @staticmethod
    def fromconfig(repo):
        return True


_has_zstd = (
    b'zstd' in util.compengines
    and util.compengines[b'zstd'].available()
    and util.compengines[b'zstd'].revlogheader()
)


@registerformatvariant
class compressionengine(formatvariant):
    name = b'compression'

    if _has_zstd:
        default = b'zstd'
    else:
        default = b'zlib'

    description = _(
        b'Compresion algorithm used to compress data. '
        b'Some engine are faster than other'
    )

    upgrademessage = _(
        b'revlog content will be recompressed with the new algorithm.'
    )

    @classmethod
    def fromrepo(cls, repo):
        # we allow multiple compression engine requirement to co-exist because
        # strickly speaking, revlog seems to support mixed compression style.
        #
        # The compression used for new entries will be "the last one"
        compression = b'zlib'
        for req in repo.requirements:
            prefix = req.startswith
            if prefix(b'revlog-compression-') or prefix(b'exp-compression-'):
                compression = req.split(b'-', 2)[2]
        return compression

    @classmethod
    def fromconfig(cls, repo):
        compengines = repo.ui.configlist(b'format', b'revlog-compression')
        # return the first valid value as the selection code would do
        for comp in compengines:
            if comp in util.compengines:
                e = util.compengines[comp]
                if e.available() and e.revlogheader():
                    return comp

        # no valide compression found lets display it all for clarity
        return b','.join(compengines)


@registerformatvariant
class compressionlevel(formatvariant):
    name = b'compression-level'
    default = b'default'

    description = _(b'compression level')

    upgrademessage = _(b'revlog content will be recompressed')

    @classmethod
    def fromrepo(cls, repo):
        comp = compressionengine.fromrepo(repo)
        level = None
        if comp == b'zlib':
            level = repo.ui.configint(b'storage', b'revlog.zlib.level')
        elif comp == b'zstd':
            level = repo.ui.configint(b'storage', b'revlog.zstd.level')
        if level is None:
            return b'default'
        return b"%d" % level

    @classmethod
    def fromconfig(cls, repo):
        comp = compressionengine.fromconfig(repo)
        level = None
        if comp == b'zlib':
            level = repo.ui.configint(b'storage', b'revlog.zlib.level')
        elif comp == b'zstd':
            level = repo.ui.configint(b'storage', b'revlog.zstd.level')
        if level is None:
            return b'default'
        return b"%d" % level


def find_format_upgrades(repo):
    """returns a list of format upgrades which can be perform on the repo"""
    upgrades = []

    # We could detect lack of revlogv1 and store here, but they were added
    # in 0.9.2 and we don't support upgrading repos without these
    # requirements, so let's not bother.

    for fv in allformatvariant:
        if not fv.fromrepo(repo):
            upgrades.append(fv)

    return upgrades


def find_format_downgrades(repo):
    """returns a list of format downgrades which will be performed on the repo
    because of disabled config option for them"""

    downgrades = []

    for fv in allformatvariant:
        if fv.name == b'compression':
            # If there is a compression change between repository
            # and config, destination repository compression will change
            # and current compression will be removed.
            if fv.fromrepo(repo) != fv.fromconfig(repo):
                downgrades.append(fv)
            continue
        # format variant exist in repo but does not exist in new repository
        # config
        if fv.fromrepo(repo) and not fv.fromconfig(repo):
            downgrades.append(fv)

    return downgrades


ALL_OPTIMISATIONS = []


def register_optimization(obj):
    ALL_OPTIMISATIONS.append(obj)
    return obj


class optimization(improvement):
    """an improvement subclass dedicated to optimizations"""

    type = OPTIMISATION


@register_optimization
class redeltaparents(optimization):
    name = b're-delta-parent'

    type = OPTIMISATION

    description = _(
        b'deltas within internal storage will be recalculated to '
        b'choose an optimal base revision where this was not '
        b'already done; the size of the repository may shrink and '
        b'various operations may become faster; the first time '
        b'this optimization is performed could slow down upgrade '
        b'execution considerably; subsequent invocations should '
        b'not run noticeably slower'
    )

    upgrademessage = _(
        b'deltas within internal storage will choose a new '
        b'base revision if needed'
    )


@register_optimization
class redeltamultibase(optimization):
    name = b're-delta-multibase'

    type = OPTIMISATION

    description = _(
        b'deltas within internal storage will be recalculated '
        b'against multiple base revision and the smallest '
        b'difference will be used; the size of the repository may '
        b'shrink significantly when there are many merges; this '
        b'optimization will slow down execution in proportion to '
        b'the number of merges in the repository and the amount '
        b'of files in the repository; this slow down should not '
        b'be significant unless there are tens of thousands of '
        b'files and thousands of merges'
    )

    upgrademessage = _(
        b'deltas within internal storage will choose an '
        b'optimal delta by computing deltas against multiple '
        b'parents; may slow down execution time '
        b'significantly'
    )


@register_optimization
class redeltaall(optimization):
    name = b're-delta-all'

    type = OPTIMISATION

    description = _(
        b'deltas within internal storage will always be '
        b'recalculated without reusing prior deltas; this will '
        b'likely make execution run several times slower; this '
        b'optimization is typically not needed'
    )

    upgrademessage = _(
        b'deltas within internal storage will be fully '
        b'recomputed; this will likely drastically slow down '
        b'execution time'
    )


@register_optimization
class redeltafulladd(optimization):
    name = b're-delta-fulladd'

    type = OPTIMISATION

    description = _(
        b'every revision will be re-added as if it was new '
        b'content. It will go through the full storage '
        b'mechanism giving extensions a chance to process it '
        b'(eg. lfs). This is similar to "re-delta-all" but even '
        b'slower since more logic is involved.'
    )

    upgrademessage = _(
        b'each revision will be added as new content to the '
        b'internal storage; this will likely drastically slow '
        b'down execution time, but some extensions might need '
        b'it'
    )


def findoptimizations(repo):
    """Determine optimisation that could be used during upgrade"""
    # These are unconditionally added. There is logic later that figures out
    # which ones to apply.
    return list(ALL_OPTIMISATIONS)


def determine_upgrade_actions(
    repo, format_upgrades, optimizations, sourcereqs, destreqs
):
    """Determine upgrade actions that will be performed.

    Given a list of improvements as returned by ``find_format_upgrades`` and
    ``findoptimizations``, determine the list of upgrade actions that
    will be performed.

    The role of this function is to filter improvements if needed, apply
    recommended optimizations from the improvements list that make sense,
    etc.

    Returns a list of action names.
    """
    newactions = []

    for d in format_upgrades:
        if hasattr(d, '_requirement'):
            name = d._requirement
        else:
            name = None

        # If the action is a requirement that doesn't show up in the
        # destination requirements, prune the action.
        if name is not None and name not in destreqs:
            continue

        newactions.append(d)

    newactions.extend(
        o
        for o in sorted(optimizations, key=(lambda x: x.name))
        if o not in newactions
    )

    # FUTURE consider adding some optimizations here for certain transitions.
    # e.g. adding generaldelta could schedule parent redeltas.

    return newactions


class BaseOperation:
    """base class that contains the minimum for an upgrade to work

    (this might need to be extended as the usage for subclass alternative to
    UpgradeOperation extends)
    """

    def __init__(
        self,
        new_requirements,
        backup_store,
    ):
        self.new_requirements = new_requirements
        # should this operation create a backup of the store
        self.backup_store = backup_store


class UpgradeOperation(BaseOperation):
    """represent the work to be done during an upgrade"""

    def __init__(
        self,
        ui,
        new_requirements,
        current_requirements,
        upgrade_actions,
        removed_actions,
        revlogs_to_process,
        backup_store,
    ):
        super().__init__(
            new_requirements,
            backup_store,
        )
        self.ui = ui
        self.current_requirements = current_requirements
        # list of upgrade actions the operation will perform
        self.upgrade_actions = upgrade_actions
        self.removed_actions = removed_actions
        self.revlogs_to_process = revlogs_to_process
        # requirements which will be added by the operation
        self._added_requirements = (
            self.new_requirements - self.current_requirements
        )
        # requirements which will be removed by the operation
        self._removed_requirements = (
            self.current_requirements - self.new_requirements
        )
        # requirements which will be preserved by the operation
        self._preserved_requirements = (
            self.current_requirements & self.new_requirements
        )
        # optimizations which are not used and it's recommended that they
        # should use them
        all_optimizations = findoptimizations(None)
        self.unused_optimizations = [
            i for i in all_optimizations if i not in self.upgrade_actions
        ]

        # delta reuse mode of this upgrade operation
        upgrade_actions_names = self.upgrade_actions_names
        self.delta_reuse_mode = revlog.revlog.DELTAREUSEALWAYS
        if b're-delta-all' in upgrade_actions_names:
            self.delta_reuse_mode = revlog.revlog.DELTAREUSENEVER
        elif b're-delta-parent' in upgrade_actions_names:
            self.delta_reuse_mode = revlog.revlog.DELTAREUSESAMEREVS
        elif b're-delta-multibase' in upgrade_actions_names:
            self.delta_reuse_mode = revlog.revlog.DELTAREUSESAMEREVS
        elif b're-delta-fulladd' in upgrade_actions_names:
            self.delta_reuse_mode = revlog.revlog.DELTAREUSEFULLADD

        # should this operation force re-delta of both parents
        self.force_re_delta_both_parents = (
            b're-delta-multibase' in upgrade_actions_names
        )

    @property
    def upgrade_actions_names(self):
        return set([a.name for a in self.upgrade_actions])

    @property
    def requirements_only(self):
        # does the operation only touches repository requirement
        return (
            self.touches_requirements
            and not self.touches_filelogs
            and not self.touches_manifests
            and not self.touches_changelog
            and not self.touches_dirstate
        )

    @property
    def touches_filelogs(self):
        for a in self.upgrade_actions:
            # in optimisations, we re-process the revlogs again
            if a.type == OPTIMISATION:
                return True
            elif a.touches_filelogs:
                return True
        for a in self.removed_actions:
            if a.touches_filelogs:
                return True
        return False

    @property
    def touches_manifests(self):
        for a in self.upgrade_actions:
            # in optimisations, we re-process the revlogs again
            if a.type == OPTIMISATION:
                return True
            elif a.touches_manifests:
                return True
        for a in self.removed_actions:
            if a.touches_manifests:
                return True
        return False

    @property
    def touches_changelog(self):
        for a in self.upgrade_actions:
            # in optimisations, we re-process the revlogs again
            if a.type == OPTIMISATION:
                return True
            elif a.touches_changelog:
                return True
        for a in self.removed_actions:
            if a.touches_changelog:
                return True
        return False

    @property
    def touches_requirements(self):
        for a in self.upgrade_actions:
            # optimisations are used to re-process revlogs and does not result
            # in a requirement being added or removed
            if a.type == OPTIMISATION:
                pass
            elif a.touches_requirements:
                return True
        for a in self.removed_actions:
            if a.touches_requirements:
                return True

    @property
    def touches_dirstate(self):
        for a in self.upgrade_actions:
            # revlog optimisations do not affect the dirstate
            if a.type == OPTIMISATION:
                pass
            elif a.touches_dirstate:
                return True
        for a in self.removed_actions:
            if a.touches_dirstate:
                return True

        return False

    def _write_labeled(self, l, label: bytes):
        """
        Utility function to aid writing of a list under one label
        """
        first = True
        for r in sorted(l):
            if not first:
                self.ui.write(b', ')
            self.ui.write(r, label=label)
            first = False

    def print_requirements(self):
        self.ui.write(_(b'requirements\n'))
        self.ui.write(_(b'   preserved: '))
        self._write_labeled(
            self._preserved_requirements, b"upgrade-repo.requirement.preserved"
        )
        self.ui.write((b'\n'))
        if self._removed_requirements:
            self.ui.write(_(b'   removed: '))
            self._write_labeled(
                self._removed_requirements, b"upgrade-repo.requirement.removed"
            )
            self.ui.write((b'\n'))
        if self._added_requirements:
            self.ui.write(_(b'   added: '))
            self._write_labeled(
                self._added_requirements, b"upgrade-repo.requirement.added"
            )
            self.ui.write((b'\n'))
        self.ui.write(b'\n')

    def print_optimisations(self):
        optimisations = [
            a for a in self.upgrade_actions if a.type == OPTIMISATION
        ]
        optimisations.sort(key=lambda a: a.name)
        if optimisations:
            self.ui.write(_(b'optimisations: '))
            self._write_labeled(
                [a.name for a in optimisations],
                b"upgrade-repo.optimisation.performed",
            )
            self.ui.write(b'\n\n')

    def print_upgrade_actions(self):
        for a in self.upgrade_actions:
            self.ui.status(b'%s\n   %s\n\n' % (a.name, a.upgrademessage))

    def print_affected_revlogs(self):
        if not self.revlogs_to_process:
            self.ui.write((b'no revlogs to process\n'))
        else:
            self.ui.write((b'processed revlogs:\n'))
            for r in sorted(self.revlogs_to_process):
                self.ui.write((b'  - %s\n' % r))
        self.ui.write((b'\n'))

    def print_unused_optimizations(self):
        for i in self.unused_optimizations:
            self.ui.status(_(b'%s\n   %s\n\n') % (i.name, i.description))

    def has_upgrade_action(self, name):
        """Check whether the upgrade operation will perform this action"""
        return name in self._upgrade_actions_names

    def print_post_op_messages(self):
        """print post upgrade operation warning messages"""
        for a in self.upgrade_actions:
            if a.postupgrademessage is not None:
                self.ui.warn(b'%s\n' % a.postupgrademessage)
        for a in self.removed_actions:
            if a.postdowngrademessage is not None:
                self.ui.warn(b'%s\n' % a.postdowngrademessage)


###  Code checking if a repository can got through the upgrade process at all. #


def requiredsourcerequirements(repo):
    """Obtain requirements required to be present to upgrade a repo.

    An upgrade will not be allowed if the repository doesn't have the
    requirements returned by this function.
    """
    return {
        # Introduced in Mercurial 0.9.2.
        requirements.STORE_REQUIREMENT,
    }


def blocksourcerequirements(repo):
    """Obtain requirements that will prevent an upgrade from occurring.

    An upgrade cannot be performed if the source repository contains a
    requirements in the returned set.
    """
    return {
        # This was a precursor to generaldelta and was never enabled by default.
        # It should (hopefully) not exist in the wild.
        b'parentdelta',
    }


def check_revlog_version(reqs):
    """Check that the requirements contain at least one Revlog version"""
    all_revlogs = {
        requirements.REVLOGV1_REQUIREMENT,
        requirements.REVLOGV2_REQUIREMENT,
    }
    if not all_revlogs.intersection(reqs):
        msg = _(b'cannot upgrade repository; missing a revlog version')
        raise error.Abort(msg)


def check_source_requirements(repo):
    """Ensure that no existing requirements prevent the repository upgrade"""

    check_revlog_version(repo.requirements)
    required = requiredsourcerequirements(repo)
    missingreqs = required - repo.requirements
    if missingreqs:
        msg = _(b'cannot upgrade repository; requirement missing: %s')
        missingreqs = b', '.join(sorted(missingreqs))
        raise error.Abort(msg % missingreqs)

    blocking = blocksourcerequirements(repo)
    blockingreqs = blocking & repo.requirements
    if blockingreqs:
        m = _(b'cannot upgrade repository; unsupported source requirement: %s')
        blockingreqs = b', '.join(sorted(blockingreqs))
        raise error.Abort(m % blockingreqs)
    # Upgrade should operate on the actual store, not the shared link.

    bad_share = (
        requirements.SHARED_REQUIREMENT in repo.requirements
        and requirements.SHARESAFE_REQUIREMENT not in repo.requirements
    )
    if bad_share:
        m = _(b'cannot upgrade repository; share repository without share-safe')
        h = _(b'check :hg:`help config.format.use-share-safe`')
        raise error.Abort(m, hint=h)


### Verify the validity of the planned requirement changes ####################


def supportremovedrequirements(repo):
    """Obtain requirements that can be removed during an upgrade.

    If an upgrade were to create a repository that dropped a requirement,
    the dropped requirement must appear in the returned set for the upgrade
    to be allowed.
    """
    supported = {
        requirements.SPARSEREVLOG_REQUIREMENT,
        requirements.COPIESSDC_REQUIREMENT,
        requirements.NODEMAP_REQUIREMENT,
        requirements.SHARESAFE_REQUIREMENT,
        requirements.REVLOGV2_REQUIREMENT,
        requirements.CHANGELOGV2_REQUIREMENT,
        requirements.REVLOGV1_REQUIREMENT,
        requirements.DIRSTATE_TRACKED_HINT_V1,
        requirements.DIRSTATE_V2_REQUIREMENT,
    }
    for name in compression.compengines:
        engine = compression.compengines[name]
        if engine.available() and engine.revlogheader():
            supported.add(b'exp-compression-%s' % name)
            if engine.name() == b'zstd':
                supported.add(b'revlog-compression-zstd')
    return supported


def supporteddestrequirements(repo):
    """Obtain requirements that upgrade supports in the destination.

    If the result of the upgrade would have requirements not in this set,
    the upgrade is disallowed.

    Extensions should monkeypatch this to add their custom requirements.
    """
    supported = {
        requirements.CHANGELOGV2_REQUIREMENT,
        requirements.COPIESSDC_REQUIREMENT,
        requirements.DIRSTATE_TRACKED_HINT_V1,
        requirements.DIRSTATE_V2_REQUIREMENT,
        requirements.DOTENCODE_REQUIREMENT,
        requirements.FNCACHE_REQUIREMENT,
        requirements.GENERALDELTA_REQUIREMENT,
        requirements.NODEMAP_REQUIREMENT,
        requirements.REVLOGV1_REQUIREMENT,  # allowed in case of downgrade
        requirements.REVLOGV2_REQUIREMENT,
        requirements.SHARED_REQUIREMENT,
        requirements.SHARESAFE_REQUIREMENT,
        requirements.SPARSEREVLOG_REQUIREMENT,
        requirements.STORE_REQUIREMENT,
        requirements.TREEMANIFEST_REQUIREMENT,
        requirements.NARROW_REQUIREMENT,
    }
    for name in compression.compengines:
        engine = compression.compengines[name]
        if engine.available() and engine.revlogheader():
            supported.add(b'exp-compression-%s' % name)
            if engine.name() == b'zstd':
                supported.add(b'revlog-compression-zstd')
    return supported


def allowednewrequirements(repo):
    """Obtain requirements that can be added to a repository during upgrade.

    This is used to disallow proposed requirements from being added when
    they weren't present before.

    We use a list of allowed requirement additions instead of a list of known
    bad additions because the whitelist approach is safer and will prevent
    future, unknown requirements from accidentally being added.
    """
    supported = {
        requirements.DOTENCODE_REQUIREMENT,
        requirements.FNCACHE_REQUIREMENT,
        requirements.GENERALDELTA_REQUIREMENT,
        requirements.SPARSEREVLOG_REQUIREMENT,
        requirements.COPIESSDC_REQUIREMENT,
        requirements.NODEMAP_REQUIREMENT,
        requirements.SHARESAFE_REQUIREMENT,
        requirements.REVLOGV1_REQUIREMENT,
        requirements.REVLOGV2_REQUIREMENT,
        requirements.CHANGELOGV2_REQUIREMENT,
        requirements.DIRSTATE_TRACKED_HINT_V1,
        requirements.DIRSTATE_V2_REQUIREMENT,
    }
    for name in compression.compengines:
        engine = compression.compengines[name]
        if engine.available() and engine.revlogheader():
            supported.add(b'exp-compression-%s' % name)
            if engine.name() == b'zstd':
                supported.add(b'revlog-compression-zstd')
    return supported


def check_requirements_changes(repo, new_reqs):
    old_reqs = repo.requirements
    check_revlog_version(repo.requirements)
    support_removal = supportremovedrequirements(repo)
    no_remove_reqs = old_reqs - new_reqs - support_removal
    if no_remove_reqs:
        msg = _(b'cannot upgrade repository; requirement would be removed: %s')
        no_remove_reqs = b', '.join(sorted(no_remove_reqs))
        raise error.Abort(msg % no_remove_reqs)

    support_addition = allowednewrequirements(repo)
    no_add_reqs = new_reqs - old_reqs - support_addition
    if no_add_reqs:
        m = _(b'cannot upgrade repository; do not support adding requirement: ')
        no_add_reqs = b', '.join(sorted(no_add_reqs))
        raise error.Abort(m + no_add_reqs)

    supported = supporteddestrequirements(repo)
    unsupported_reqs = new_reqs - supported
    if unsupported_reqs:
        msg = _(
            b'cannot upgrade repository; do not support destination '
            b'requirement: %s'
        )
        unsupported_reqs = b', '.join(sorted(unsupported_reqs))
        raise error.Abort(msg % unsupported_reqs)
