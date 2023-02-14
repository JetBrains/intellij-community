## Threading Model Helper ##

Instruments methods annotated with 
- `@RequiresEdt`
- `@RequiresBackgroundThread`
- `@RequiresReadLock`
- `@RequiresWriteLock` 
- `@RequiresReadLockAbsence` 

by inserting 
- `Application#assertIsDispatchThread()` 
- `Application#assertIsNonDispatchThread()` 
- `Application#assertReadAccessAllowed()` 
- `Application#assertWriteAccessAllowed()`
- `Application#assertReadAccessNotAllowed()`

calls accordingly. 

To disable the instrumentation use **tmh.instrument.annotations** key in the Registry.

### Limitations: ###

- Only Java code is instrumented, Kotlin instrumentation is coming soon.