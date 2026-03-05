// FIX: Convert to sealed interface
sealed class Event<caret> {
    class Click : Event()
    class Scroll : Event()
}