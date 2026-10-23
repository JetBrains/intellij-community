# Kotlin Plugin

## Inspections

Before you write, change, or review an inspection, read [Code insight: Inspections](docs/fir-ide/architecture/code-insights.md#inspections).
It covers the base class, registration, descriptions, messages, tests, and the quality checklist.

Apply the quality checklist to each new inspection and to each fix of an inspection.
Add a negative test for each checklist category that applies.

## Quick fixes

Before you write, change, or review a quick fix for a compiler diagnostic, read [Code insight: Quick Fixes](docs/fir-ide/architecture/code-insights.md#quick-fixes).
It covers the factory, the action, registration, tests, test data generation, and the quality checklist.

## Other code insight

For intentions, completion, and other code insight, read the other sections of [Code insight](docs/fir-ide/architecture/code-insights.md).

## Test data

Before you write test data for an inspection, a quick fix, or an intention, read [Test data coverage](docs/fir-ide/architecture/code-insights.md#test-data-coverage).
Plan the cases first. Reuse the Kotlin compiler tests for the same diagnostic.
