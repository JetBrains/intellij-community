allprojects {
	group = "com.h0tk3y.mpp.demo"
	version = "1.0"

	repositories {
        {{kts_kotlin_plugin_repositories}}
	}

	plugins.apply("maven-publish")
	the<PublishingExtension>().repositories.maven("$rootDir/repo")
}