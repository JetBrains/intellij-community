## [essential-modules.xml](essential-modules.xml)

A set of product modules that are essential for any IDE based on IJ Platform.

## [common-ide-modules.xml](common-ide-modules.xml) 

A set of product modules for regular IDE. For example, for WebStorm, but not for Fleet backend.

This set includes [essential-modules](#essential-modulesxml) and [vcs-modules](#vcs-modulesxml-) sets.

## [vcs-modules.xml](vcs-modules.xml) 

A set of product modules for regular IDE with VCS support.
This is a separate set because, for instance, `intellij.platform.smRunner.vcs` should not be included in Rider,
yet we still want to avoid duplicating the list of VCS modules.

This set is included in the [common-ide-modules](#common-ide-modulesxml-) set.