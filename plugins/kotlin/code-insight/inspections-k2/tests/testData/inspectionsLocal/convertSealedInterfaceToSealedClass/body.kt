// FIX: Convert to sealed class
sealed interface Event<caret> {
    class Click : Event
    class Scroll : Event
}