object AdventureLogger {
    private val logEntries = mutableListOf<String>()

    fun addEvent(event: String, status: String = "new"): String {
        val entry = "$event | Status: $status"
        logEntries.add(entry)
        return entry
    }

    fun updateStatus(eventIndex: Int, newStatus: String): String {
        if (eventIndex in logEntries.indices) {
            val oldEntry = logEntries[eventIndex]
            val updatedEntry = oldEntry.replace(Regex("Status: [\\w\\s]+"), "Status: $newStatus")
            logEntries[eventIndex] = updatedEntry
            return updatedEntry
        }
        return "Invalid event index"
    }

    fun getLog(): List<String> = logEntries

    fun getLoggedEvents(): Set<String> {
        return logEntries.map { it.substringBefore(" | Status:") }.toSet()
    }
}