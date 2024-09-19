Octopus Merge Support
=====================

This will be moderately complicated, as we'll need to synthesize phony
changeset entries to explode the octopus into "revisions" that only
have two parents each. For today, we can probably just do something like

    aaaaaaaaaaaaaaaaaaXX{20 bytes of exploded node's hex sha}

where XX is a counter (so we could have as many as 255 parents in a
git commit - more than I think we'd ever see.) That means that we can
install some check in this extension to disallow checking out or
otherwise interacting with the `aaaaaaaaaaaaaaaaaa` revisions.


Interface Creation
====================

We at least need an interface definition for `changelog` in core that
this extension can satisfy, and again for `basicstore`.


Reason About Locking
====================

We should spend some time thinking hard about locking, especially on
.git/index etc. We're probably adequately locking the _git_
repository, but may not have enough locking correctness in places
where hg does locking that git isn't aware of (notably the working
copy, which I believe Git does not lock.)
