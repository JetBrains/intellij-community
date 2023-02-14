Address commentary in manifest.excludedmanifestrevlog.add -
specifically we should improve the collaboration with core so that
add() never gets called on an excluded directory and we can improve
the stand-in to raise a ProgrammingError.

Reason more completely about rename-filtering logic in
narrowfilelog. There could be some surprises lurking there.

Formally document the narrowspec format. For bonus points, unify with the
server-specified narrowspec format.

narrowrepo.setnarrowpats() or narrowspec.save() need to make sure
they're holding the wlock.

The follinwg places do an unrestricted dirstate walk (including files outside the
narrowspec). Some of them should perhaps not do that.

 * debugfileset
 * perfwalk
 * sparse (but restricted to sparse config)
 * largefiles
