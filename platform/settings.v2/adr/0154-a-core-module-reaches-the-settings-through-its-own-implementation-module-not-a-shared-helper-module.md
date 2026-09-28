# 0154. A core module reaches the settings through its own implementation module, not a shared helper module

Date: 2026-10-15

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The settings modules are content modules, not core modules. A core module, such as `lang-impl`, cannot depend on
them. To use the settings, a core module declares a service interface, and another module implements it with the
settings API. A first migration of the file colors put this implementation into the settings modules.

## Decision

**The platform gives no shared helper module for these implementations. Each adopter creates its own module that
implements the service of its core module with the settings API.**

## Consequences

- The settings modules hold no code of the features that use them.
- A feature that migrates from a core module adds one small module of its own.
- The dependencies of one feature do not reach the other features.

## Rejected alternatives

### One shared helper module for all the core modules

One module would collect the dependencies of every adopter. A feature that loads the module would also load the
dependencies of all the other features. That is not good for everyone.

### The implementations live in the settings modules

The settings modules would depend on every feature that uses them.
