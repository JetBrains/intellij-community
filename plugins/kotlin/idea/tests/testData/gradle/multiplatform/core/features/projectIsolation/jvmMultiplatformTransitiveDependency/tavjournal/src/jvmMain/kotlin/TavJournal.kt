object TavJournal {
    fun recordEvent() {
        val newEvent = AdventureLog.addAdventureEvent()
        println("📜 New journal entry: $newEvent")
    }

    fun updateEvent(index: Int, newStatus: String) {
        val updatedEvent = AdventureLog.updateAdventureStatus(index, newStatus)
        println("🧙‍♂️✨🔄 Updated journal entry: $updatedEvent 🧚‍♀️✨")
    }

    fun showJournal() {
        println("\n📖 Adventure Journal:")
        AdventureLog.getJournal().forEachIndexed { index, entry ->
            println("${index + 1}. $entry")
        }
    }
}
