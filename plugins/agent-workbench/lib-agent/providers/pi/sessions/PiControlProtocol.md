# Pi Control Protocol

The PI extension control channel is a WebSocket bridge between the bundled PI extension and the IDE-side agent session provider.
The wire format is JSON with a string `type` field. Those strings are part of the protocol boundary and must stay stable.

IDE-side routing is intentionally explicit:

- `PiExtensionControlBridge` owns the WebSocket lifecycle, authentication, connected-session registry, pending request correlation, and session update events.
- `PiExtensionControlProtocol` owns the typed built-in message enum, payload parsing, extension request envelope, and shared response builders.
- `PiControlRequestHandler` extensions own domain-specific extension requests. The Agent Workbench Pi module registers
  `PiTaskFolderControlHandler` for `taskFolderRequest` frames backed by `AgentTaskFolderService`.

Do not add reflection-based dispatch here. New built-in Pi transport commands should be routed from `PiControlMessageType` by explicit `when` branches;
domain-specific Agent Workbench requests should get focused `PiControlRequestHandler` implementations.
This keeps PI transport concerns separate from task-folder domain state and avoids growing the bridge into a single control object.
