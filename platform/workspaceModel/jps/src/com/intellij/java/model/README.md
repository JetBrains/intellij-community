This package supposed to be located in the java plugin.
For the moment, it's located in the platform as for correct entities serialization we have to collect all
serializers from different plugins using the extension points. However, the EPs are not available in the build process at the moment.

This package should be moved to a java plugin with an appropriate module as the EPs will be available in the JPS build process.
https://youtrack.jetbrains.com/issue/IDEA-322189