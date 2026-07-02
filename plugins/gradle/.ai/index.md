# Gradle Plugin Coding Agent Guidelines

Routing notes for AI coding agents working under `community/plugins/gradle/`.
Repository-wide rules in top-level `AGENTS.md` / `CLAUDE.md` always apply —
this file adds Gradle-specific overlays only.

## Docs

Long-form documentation lives under [`../docs/`](../docs) (relative to this file):

- [Tests layout](../docs/tests-layout.md) — aggregator meta-modules (`intellij.gradle.tests.main`, `intellij.gradle.kotlin.tests.main`, `intellij.gradle.tests.benchmark.main`), where to put new tests, and how to find the TeamCity test group for a test.
- [Benchmark tests](../docs/benchmark-tests.md) — step-by-step guide for defining a new `intellij.gradle.<area>.tests.benchmark` module and wiring it into the `intellij.gradle.tests.benchmark.main` aggregator.
- [K2-Gradle tests](../kotlin/README.md) — specifics of the `intellij.gradle.kotlin.tests.main` aggregator.

## Routing

| Task                                                       | Start here                                                                                                                        |
|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Adding a new benchmark (performance) test module           | [../docs/benchmark-tests.md](../docs/benchmark-tests.md)                                                                          |
| Deciding where a new Gradle test should live               | [../docs/tests-layout.md](../docs/tests-layout.md)                                                                                |
| Finding the TeamCity group for a Gradle test               | [../docs/tests-layout.md](../docs/tests-layout.md)                                                                                |
| K2-Gradle tests specifics                                  | [../kotlin/README.md](../kotlin/README.md)                                                                                        |

## Updating this file

Keep this file terse — a routing index, not documentation. Put long-form
material into `community/plugins/gradle/docs/*.md` and link to it from here.
