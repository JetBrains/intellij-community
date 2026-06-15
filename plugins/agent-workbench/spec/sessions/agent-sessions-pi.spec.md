---
name: Agent Workbench Pi Sessions
description: Requirements for first-class pi.dev session support in Agent Workbench.
targets:
  - ../../common/src/session/AgentSessionModels.kt
  - ../../pi/sessions/resources/intellij.agent.workbench.pi.sessions.xml
  - ../../pi/sessions-filewatch/resources/intellij.agent.workbench.pi.sessions.filewatch.xml
  - ../../pi/sessions/resources/pi-extension/agent-workbench-extension.ts
  - ../../pi/sessions/src/**/*.kt
  - ../../pi/sessions/testSrc/*.kt
  - ../../sessions-core/src/providers/AgentSessionProviderDescriptor.kt
  - ../../sessions/src/settings/*.kt
  - ../../sessions/testSrc/settings/*.kt
  - ../../pi/sessions-filewatch/src/**/*.kt
  - ../../pi/sessions-filewatch/testSrc/*.kt
  - ../../plugin/resources/META-INF/plugin.xml
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
---

# Agent Workbench Pi Sessions

Status: Accepted
Date: 2026-06-05

## Summary
Agent Workbench treats Pi as a first-class terminal-backed provider. Pi sessions are discovered from Pi JSONL session files, can be launched and resumed from Agent Workbench, and support rename/archive state without requiring a Pi-specific backend API.

## Requirements
- Pi must be exposed as `AgentSessionProvider.PI`, registered after Junie and before Terminal, and shown in provider menus with a Pi icon and localized labels.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt
  [@test] ../../plugin/testSrc/AgentWorkbenchProviderRegistrationTest.kt

- Pi launch support must use the shared Terminal agent resolver for the `pi` executable without registering Pi as a built-in Terminal agent. New Agent Workbench sessions must launch `pi --extension <agentWorkbenchExtension> --session-id <uuid>` with a provider-allocated session id, while resumed sessions must launch `pi --extension <agentWorkbenchExtension> --session <threadId>`. The launch environment must pass `AGENT_WORKBENCH_PI_THEME_STATE=<stateFile>`, `AGENT_WORKBENCH_PI_STATUS_ENDPOINT=<url>`, and a launch-scoped `AGENT_WORKBENCH_PI_STATUS_TOKEN=<token>` when the Agent Workbench-managed Pi extension is available; the token must be retained only in memory by the IDE bridge, must not be persisted in tab state, and must be invalidated when the live terminal session closes. If the extension cannot be materialized, launch must continue without `--extension`.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Pi oMLX model support must be enabled by default but controllable through a Pi provider setting under Agent Workbench > Providers. When enabled, Pi discovers local oMLX generation models from PI auth and `.omlx/settings.json`; PI auth is preferred for a matching base URL, but `.omlx/settings.json` must remain a fallback if the PI auth endpoint does not return usable generation models. A selected oMLX model launches with `--provider`, `--model`, and `AGENT_WORKBENCH_PI_OMLX_PROVIDER` metadata so the bundled Pi extension can register the selected local provider. When disabled, model selection is hidden, oMLX catalog refresh is skipped, saved oMLX selections sanitize back to provider default, and launches must not include oMLX registration metadata.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/settings/AgentSessionProviderSettingsServiceTest.kt
  [@test] ../../sessions/testSrc/settings/AgentWorkbenchSettingsConfigurableTest.kt

- When Pi generation model selection is enabled, Agent Workbench must scope launched Pi sessions from Pi's own `--list-models` output after loading the managed extension and Agent Workbench model-registration metadata. The resulting `--models` scope must keep the selected model first and include Pi built-in/custom models together with Agent Workbench-registered oMLX/JetBrains Central models, so Pi's `/model` selector is not limited to extension-provided models.
  Pi-provided rows from the popup catalog are still persisted as Agent Workbench generation model ids (for example, encoded `pi:` rows), not as raw Pi CLI model strings. New-session launches, prompt launches into existing threads, editor restore, and pending/concrete tab resume must all replay the persisted Agent Workbench generation settings through the shared launch planner before constructing the Pi command, so an Agent Workbench-selected model wins over later runtime `/model` changes made inside Pi.
  Pi-reported Claude rows must be filtered before they enter the Agent Workbench catalog or are recoded as JetBrains Central fallback rows: hide all Claude 3 rows, all Claude rows with parsed version lower than 4.6, and all Claude rows containing an 8-digit `20xx` date token. Profile-backed Central rows are not filtered by this Pi row rule.
  JetBrains Central launch metadata must be resolved from `jbcentral status`, including the proxy port and the supported wired agent set. Wired `Codex` must register Central models from Pi's `openai-codex` source through the `codex/openai` proxy route; wired `Claude Code` must register Central models from Pi's `anthropic` source through the `claude-code/anthropic` proxy route. Both routes must be presented to users under the single `JetBrains Central` provider name, including Claude Code models such as Opus.
  JetBrains Central rows should be derived from available JetBrains Central LLM profiles through the direct local `jbcentral` proxy probe only when it is explicitly enabled, not from Pi's static model registry: real `profilesV8` rows can omit `providerModelID`, so Agent Workbench must use `providerModelID` only when present and otherwise launch by the Central profile id such as `openai-gpt-5` or `anthropic-claude-4-5-sonnet`. OpenAI/Codex Central profiles may be `Responses`-only and must not be filtered out for missing `Chat`; Anthropic/Claude Code profiles must still support chat. Deprecated and experimental Central profiles must stay hidden.
  When profile-backed JetBrains Central models are available, Pi's Central-looking fallback rows (`JetBrains Central`, `openai-codex`, and wired `anthropic`) must be suppressed so stale or future entries such as unavailable Fable models do not appear as normal choices. If the direct profiles probe is disabled or returns no profiles, Agent Workbench must still wire JetBrains Central into Pi through the managed extension, use Pi's static `openai-codex`/`anthropic` model registry as a last-resort fallback, and recode those rows as `JetBrains Central` models before showing or launching them. If the full Pi catalog probe fails, launch must fall back to the discovered Agent Workbench extension models.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt
  [@test] ../../pi/sessions/testSrc/PiJbCentralModelCatalogTest.kt
  [@test] ../../pi/sessions/testSrc/PiKnownModelCatalogTest.kt

- JetBrains Central model availability source order is: direct `profilesV8` through the local `jbcentral` proxy, then Pi static Central rows only as a last-resort fallback. Pi static rows are not authoritative for Central availability; they may be shown only when no profile-backed Central list is available, and they must be suppressed as soon as profile-backed rows exist.
  The direct local `profilesV8` probe must stay disabled by default behind `-Dagent.workbench.pi.jbcentral.direct.profiles.enabled=true` while the local `jbcentral` proxy does not expose that endpoint consistently across installed JBCentral versions and environments. This avoids repeatedly probing known-404 local proxy routes.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt
  [@test] ../../pi/sessions/testSrc/PiJbCentralModelCatalogTest.kt

- The bundled Pi extension is the bridge between Agent Workbench catalog rows and Pi's `/model` selector. During catalog probing, Agent Workbench must pass profile-backed oMLX/JetBrains Central models through `AGENT_WORKBENCH_PI_MODEL_CATALOG`; the extension registers those providers before Pi evaluates `--list-models`. JetBrains Central proxy credentials must not be persisted in model ids, launch metadata, or logs; the extension and IDE-side direct profile probe should obtain the wire secret from `jbcentral proxy start --return-key` and fall back to `.wire/config.json` when the proxy is already running but the CLI cannot return a key.
  [@test] ../../pi/sessions/testSrc/PiKnownModelCatalogTest.kt
  [@test] ../../pi/sessions/testSrc/PiJbCentralModelCatalogTest.kt

- Agent Workbench must provide one bundled Pi extension for IDE integration. It reads a managed JSON theme snapshot, validates all required Pi foreground/background theme keys, applies a full Pi `Theme`, and watches the managed theme-state file for IDE theme changes. The snapshot must be built from the active IDE look-and-feel, Swing UI defaults, and editor color scheme so Islands Dark, Islands Light, Islands Darcula, High Contrast, and customized user colors are reflected at runtime. Theme state must be refreshed on both IDE look-and-feel and editor color scheme changes. The extension resource is materialized into a managed IDE system-cache directory with a non-JSON sidecar manifest containing a format version and SHA-256 hash.
  [@test] ../../pi/sessions/testSrc/PiThemeSupportTest.kt
  [@test] ../../pi/sessions/testSrc/PiThemeSnapshotBuilderTest.kt

- Initial prompts must be passed as a positional Pi CLI message argument. Pi must not expose built-in Agent Workbench plan mode or YOLO launch modes.
  [@test] ../../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Pi session loading must read JSONL files from the effective Pi session directory: `PI_CODING_AGENT_SESSION_DIR`, then project/global Pi `settings.json` `sessionDir`, then the default `~/.pi/agent/sessions/<encoded-cwd>` directory.
  [@test] ../../pi/sessions/testSrc/PiSessionSourceTest.kt

- Listed Pi threads must be filtered by the session header `cwd`, use the header `id`, use the latest `session_info.name` as the title, fall back to the first user message, and sort newest first by latest user/assistant activity timestamp, header timestamp, or file mtime.
  [@test] ../../pi/sessions/testSrc/PiSessionSourceTest.kt

- Pi thread activity must be inferred from the persisted JSONL leaf. User, tool-result, custom, and assistant `toolUse` leaves are shown
  as `PROCESSING`; completed assistant leaves use the shared Done/read semantics when observed work completes. Pi does not report
  `NEEDS_INPUT` from session JSONL until an explicit persisted status signal exists.
  [@test] ../../pi/sessions/testSrc/PiSessionSourceTest.kt

- The bundled Pi extension must also post fast activity hints on Pi lifecycle events to the IDE-local status endpoint. Requests must be `POST` calls from an accessible local origin, authenticated with `Authorization: Bearer <token>`, and accepted only when the token is bound to the posted Pi `sessionId`. Accepted requests emit scoped, thread-scoped `HINTS_CHANGED` activity updates; `done` updates use the shared Done/read semantics. Malformed, oversized, unauthorized, or cross-session requests must not emit updates. JSONL file watching is an opt-in durable fallback for persisted session and archive-state changes, isolated in a separate content module and disabled by default behind `agent.workbench.pi.file.watch.fallback`.
  [@test] ../../pi/sessions/testSrc/PiExtensionStatusHttpRequestHandlerTest.kt
  [@test] ../../pi/sessions-filewatch/testSrc/PiSessionFileWatchUpdateEventsContributorTest.kt

- Rename must append a Pi-compatible `session_info` entry to the session JSONL file. Archive and unarchive must persist Agent Workbench sidecar state keyed by normalized project path and session id.
  [@test] ../../pi/sessions/testSrc/PiSessionSourceTest.kt

## Theme Mapping Notes
- Runtime IDE/editor colors are the preferred source for every Pi theme key. Fallback palettes are allowed only when a corresponding UI default or editor color is unavailable, when the IDE theme cannot be resolved, or when the Pi extension starts without a readable state file.
- Semantic foregrounds come from `Label.successForeground`/`Label.errorForeground`/`Label.warningForeground`; secondary/tertiary text from `Component.infoForeground` (muted) and `Label.disabledForeground` (dim); feedback backgrounds from `Banner.successBackground`/`Banner.errorBackground`/`Banner.aiBackground`.
- Pi colors every completed tool block with `toolSuccessBg` (and failed ones with `toolErrorBg`); this is Pi's native success semantics, not an IDE banner. The `toolSuccessBg ← Banner.successBackground` mapping is intentional: in Islands themes it resolves to the theme's own subtle success surface (e.g. `green-160` `#F5FAF3` in Islands Light), which is less saturated than Pi's built-in light theme (`#E8F0E8`), so completed blocks stay theme-conformant.
- Pi renders inside the IDE terminal, so the terminal default colors (console scheme `NORMAL_OUTPUT`/`CONSOLE_BACKGROUND_KEY`, falling back to editor defaults) are the base for default text, alpha blending, and darkness detection. The editor caret-row color backs user messages so they stay distinguishable from the terminal background; the Pi extension delegates theme tokens unknown to the snapshot to the built-in Pi theme for forward compatibility.
- The maintained fallback variants are Islands Dark, Islands Light, Islands Darcula, and High Contrast. Unknown custom themes fall back by darkness while still using the runtime colors that are available.
- When syncing with Pi theme API changes, update the Kotlin snapshot builder, TypeScript snapshot validation, extension fallback snapshot, and tests in the same change. The required key lists in the Pi extension and `PiThemeSnapshotBuilderTest` must stay aligned with Pi `ThemeColor` and `ThemeBg` definitions.
- Any hardcoded or blended color should be treated as a documented fallback, not as the primary mapping. Prefer replacing such values with stable IDE/editor color keys when a suitable source becomes available.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.pi.sessions.tests --test 'com.intellij.agent.workbench.pi.sessions.*Test'`
- `./tests.cmd --module intellij.agent.workbench.pi.sessions.filewatch.tests --test 'com.intellij.agent.workbench.pi.sessions.*Test'`
- `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.plugin.AgentWorkbenchProviderRegistrationTest`

## References
- `agent-sessions.spec.md`
- `agent-terminal-sessions.spec.md`
- Pi local checkout: `/Users/develar/projects/pi-main/packages/coding-agent`
