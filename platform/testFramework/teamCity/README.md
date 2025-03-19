This module is expected to be used in the test process, which loads the application (regular tests), 
as well as in the test process, which starts the whole IDE in another process (integration tests).
`com.intellij.openapi.application.Application` must not be accessible in this module.
