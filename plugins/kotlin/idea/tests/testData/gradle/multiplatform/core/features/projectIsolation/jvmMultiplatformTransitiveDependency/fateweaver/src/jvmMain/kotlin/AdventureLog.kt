object AdventureLog {
    fun addAdventureEvent(): String {
        val newEvent = EventGenerator.getRandomEvent(AdventureLogger.getLoggedEvents())
        return AdventureLogger.addEvent(newEvent)
    }

    fun updateAdventureStatus(index: Int, newStatus: String): String {
        return AdventureLogger.updateStatus(index, newStatus)
    }

    fun getJournal(): List<String> = AdventureLogger.getLog()
}