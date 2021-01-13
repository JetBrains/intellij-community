# Library for validating events before sending them to the Analytics Platform

##Usage

```kotlin
val validator = SimpleSensitiveDataValidator("<bundled metadata>",
  buildParser = { build: String? -> EventLogBuild.fromString(build) })

val event: LogEvent = newLogEvent(session = "80bb576ed123",
  build = "203.6682.168",
  bucket = "123",
  time = System.currentTimeMillis(),
  groupId = "groupId",
  groupVersion = "42",
  recorderVersion = "1",
  eventId = "eventId",
  isState = true,
  eventData = hashMapOf("count" to 42))
val validatedEvent: LogEvent? = validator.validateEvent(event)

//…
validator.update("<loaded from server metadata>")
//...

```