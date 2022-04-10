fun main() {
    try {
    } catch (e: RuntimeException) {
        throw     e
    } catch (e: Exception) {
        throw
        e
    }
}