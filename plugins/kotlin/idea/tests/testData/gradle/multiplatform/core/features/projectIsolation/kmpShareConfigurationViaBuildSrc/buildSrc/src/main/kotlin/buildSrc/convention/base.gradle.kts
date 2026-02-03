package buildSrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    base
}


tasks.withType<Test>().configureEach {
    testLogging {
        showCauses = false
        showExceptions = false
        showStackTraces = false
        showStandardStreams = false
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
    }
}