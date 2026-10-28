# 0161. A listener notification waits until the context it carries is ready

Date: 2026-10-28

## Status

Accepted ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)).

## Context

A key is observable through a listener ([ADR 0044](./0044-a-key-is-observable-through-a-listener.md)). A contextual
listener fires when the stored value that its key resolves at its context changes. It keeps the last value it fired
for, and fires only on a net change. A key-only listener gets a value-less ping once per changed context in one model
change, with the context of the change. A consumer such as Settings Sync observes the writes of its keys anywhere,
and the context tells it where.

A context can be dirty while other contexts are ready. A reload installs its layers kind by kind
([ADR 0160](./0160-a-reload-installs-the-layers-kind-by-kind.md)), so a directory context
stays dirty while the file layer loads. During that time, a reload, a write at the application context, or a push
of the project layer can change the value at the directory context. The directory config can still override that
value when the file layer installs.

## Decision

**The vault delivers a listener notification only when the context that the notification carries is ready. A
key-only event carries the context of the change. A contextual listener carries its own context. This applies to
every change: a reload, a write and a push.**

- When the context is ready, the vault delivers the notification at once, as before.
- When the context is dirty, the vault queues the notification. Each time a context gets ready, the vault delivers
  the queued notifications of that context.
- A queued contextual listener compares the value it resolves at delivery with its last value, and fires only on a
  net change. So it fires at most once, also when several changes affected it.
- A queued key-only event fires once per pair of a key and a changed context.

## Consequences

- A contextual listener never fires for a value that a later install at its context replaces.
- A key-only event arrives when the value at its context is final. A context under it can still be dirty. A
  listener that reads there checks `isReady()` or uses `readWhenReady`.
- During a reload, a change of a project value pings the key-only listeners when the project and the application
  buckets have installed. A change of a directory config pings them when the file bucket has installed too, with
  the directory context.
- A context gets ready also after a failed or a cancelled reload, so the queue drains then too.
- The two kinds of listeners follow one rule. A contextual listener only adds the filter by its context and the
  check of the net change.
- The resulting behavior is in the [change listeners spec](../spec/change-listeners.spec.md).

## Rejected alternatives

### Queue the notifications only during a reload of their scope

A write or a push during the reload changes the value at a dirty context too. A listener there then fires early,
with a value that is not final.

### Deliver the key-only events of a reload when the whole reload ends

Every key-only event of the reload then waits for the slowest bucket, also the event of an application key. The
event already carries its context, and a listener can check the readiness of the contexts it reads.
