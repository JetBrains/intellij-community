---
name: Prompt Context Contracts
description: Contracts for prompt-context contributors, manual sources, renderers, resolver ordering, and envelope/chip formatting.
targets:
  - ../../prompt/core/src/AgentPromptContext*.kt
  - ../../prompt/core/src/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/core/src/AgentPromptManualContextSourceBridge.kt
  - ../../prompt/ui/src/AgentPromptContext*.kt
  - ../../prompt/ui/src/AgentPromptUiSessionStateService.kt
  - ../../prompt/context/resources/intellij.agent.workbench.prompt.context.xml
  - ../../prompt/core/testSrc/AgentPromptContextResolverServiceTest.kt
  - ../../prompt/ui/testSrc/AgentPromptContext*.kt
  - ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
---

# Prompt Context Contracts

Status: Draft
Date: 2026-05-09

## Summary
Prompt context is collected by contributors, normalized by the prompt UI, and rendered through built-in or extension renderers into prompt envelopes and context chips.

## Requirements
- Contributors return structured `AgentPromptContextItem` entries with stable ids, renderer ids, payloads, and optional parent/child relationships.
  [@test] ../../prompt/core/testSrc/AgentPromptContextResolverServiceTest.kt

- Resolver ordering must be deterministic and tolerant of contributor failures; one failed contributor must not block usable context from others.
  [@test] ../../prompt/core/testSrc/AgentPromptContextResolverServiceTest.kt

- Built-in renderers own editor, path, VCS, and test context envelope/chip formatting. Missing renderer support falls back to generic rendering without blocking launch.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt

- Context normalization removes invalid/blank entries, preserves meaningful payload metadata, and applies truncation metadata rather than silently dropping source context.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextNormalizationDecisionsTest.kt

- Context soft cap is enforced before launch and requires explicit user choice when exceeded.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextSoftCapPolicyTest.kt

- Prompt draft persistence must not serialize manual context; runtime-only session state may preserve context for the current IDE session.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.core.tests --test com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverServiceTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPromptContext*Test"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest`

## References
- `../actions/global-prompt-entry.spec.md`
- `../actions/add-to-agent-context.spec.md`
