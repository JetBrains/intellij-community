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


## Libs
### [libs-core.xml](libs-core.xml)

A set of library modules that are embedded into Core and bundled to all IDEs based on IJ Platform.

All library modules in this file must have `loading="embedded"`.

### [libs-misc.xml](libs-misc.xml)

A set of library modules that must NOT be embedded into Core.
Plugins that require these libraries should bundle them individually.

All libs here must not be embedded. If a library should be embedded, it should be moved to [libs-core.xml](#libs-corexml).

### [libs-ktor.xml](libs-ktor.xml)

A set of Ktor networking library modules that are embedded with the platform.
Includes ktor-io, ktor-utils, ktor-network-tls, ktor-client, and related modules.

### [libs-temporary-bundled.xml](libs-temporary-bundled.xml)

A set of library modules that are temporarily bundled with the platform but should eventually be moved elsewhere.