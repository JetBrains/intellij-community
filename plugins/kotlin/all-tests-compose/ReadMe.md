# Tests with K2 and Compose

## How to create new tests with Compose

To create tests together with Compose, it is required to create a new module for the tests.
A new module is required because the tests will have to run with the Compose IDE plugin being present at runtime,
which is not expected for regular tests.

After the module was created, add the module as runtime dependency to this `all-tests-compose` module

- The test packages shall be prefixed with `org.jetbrains.kotlin.idea.compose.k2.*`.
- The tests will run in aggregate (master & kt-master)

## How to setup the compose compiler

You can use the `tests-compose` utilities module to get access to the `composeCompilerJars`.
If the compiler is setup (e.g. for debugger evaluate expression), the passing those jars with `-Xplugin` is required.

## How to setup the compose runtime

If the compose runtime is required, then the current maven coordinates can also be found in the`tests-compose` module.