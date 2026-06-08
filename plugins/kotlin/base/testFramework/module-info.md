### Module `intellij.kotlin.base.testFramework`

Foundational test utilities and base classes for Kotlin IDE plugin tests.

This module provides the lowest-level building blocks that other Kotlin test framework modules build upon:

- **Test base classes** — `NewLightKotlinCodeInsightFixtureTestCase` for lightweight fixture-based Kotlin tests.
- **Test data infrastructure** — `@TestRoot`/`@TestMetadata`-based test data path resolution, golden-file comparison (`KotlinTestHelpers.assertEqualsToPath`), and directive parsing (`IgnoreTests`).
- **Analysis utilities** — `AnalysisUtils.ensureFilesResolved()` for forcing Analysis API resolution in tests.
- **Platform compatibility** — `AndroidStudioTestUtils` for skipping or adjusting tests when running under Android Studio.

This module contains production sources (not test-scoped) so that it can be depended upon by other test framework modules
and published for use by external plugin tests (e.g., Kotlin Notebook plugin).
