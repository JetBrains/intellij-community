# Java coverage

This module integrates two JVM coverage engines with IntelliJ IDEA. The engine sources live in separate repositories.

## Sources and dependencies

| Component               | Sources                                                                       | Dependency declaration                                                                                                                                                                           |
|-------------------------|-------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JaCoCo                  | [jacoco/jacoco](https://github.com/jacoco/jacoco)                             | `org.jacoco:org.jacoco.ant` in [the production module](intellij.java.coverage.iml) and [the test module](intellij.java.coverage.tests.iml). This includes the agent, core, and report libraries. |
| IntelliJ coverage agent | [JetBrains/intellij-coverage](https://github.com/JetBrains/intellij-coverage) | `org.jetbrains.intellij.deps:intellij-coverage-agent` in [the shared agent module](../coverage-common/intellij.platform.coverage.agent/intellij.platform.coverage.agent.iml).                    |
| IntelliJ HTML reports   | [JetBrains/coverage-report](https://github.com/JetBrains/coverage-report)     | `org.jetbrains.intellij.deps:coverage-report` in the production and test modules above.                                                                                                          |

The `.iml` files define the current versions.
The IDE adapters are [JaCoCoCoverageRunner](src/com/intellij/coverage/JaCoCoCoverageRunner.java)
and [IDEACoverageRunner](src/com/intellij/coverage/IDEACoverageRunner.java).

## Differences and limitations

| Feature           | JaCoCo                                                    | IntelliJ coverage                                                                        |
|-------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| Execution counts  | Records whether probes execute, without execution counts. | Supports execution counts with the `idea.coverage.calculate.exact.hits` registry option. |
| Coverage per test | Unavailable in this integration.                          | Available through the JUnit and TestNG listeners. Collection must be enabled.            |

[JaCoCo PR #1680](https://github.com/jacoco/jacoco/pull/1680) is the JaCoCo change required to show hits for individual condition branches.
The current adapter uses aggregate branch counts. It also needs an update to consume the additional data.
