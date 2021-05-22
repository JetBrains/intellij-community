## Threading Model Helper ##

Instruments methods annotated with 
- `@RequiresEdt`
- `@RequiresBackgroundThread`
- `@RequiresReadLock`
- `@RequiresWriteLock` 

by inserting 
- `Application#assertIsDispatchThread()` 
- `Application#assertIsNonDispatchThread()` 
- `Application#assertReadAccessAllowed()` 
- `Application#assertWriteAccessAllowed()`

calls accordingly. 

To disable the instrumentation use **tmh.instrument.annotations** key in the Registry.