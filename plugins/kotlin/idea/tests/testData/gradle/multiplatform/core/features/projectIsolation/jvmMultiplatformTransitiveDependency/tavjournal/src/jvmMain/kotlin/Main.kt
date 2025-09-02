fun <!LINE_MARKER!>main<!>() {
    println("🌟 Welcome to the Adventure Journal! 🌟")

    println("\n🎲 Rolling the dice... A new adventure begins!")
    TavJournal.recordEvent()

    println("\n🔮 Fate twists and turns... Updating an event!")
    TavJournal.updateEvent(0, "in progress")

    println("\n✨ Another tale unfolds... ✨")
    TavJournal.recordEvent()

    TavJournal.showJournal()
}
