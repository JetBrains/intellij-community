object EventGenerator {
    private val events = listOf(
        "Astarion bares his fangs. A choice must be made...",
        "Shadowheart tightens her grip on the mysterious relic.",
        "Gale suddenly feels hungry... for magic.",
        "Lae’zel glares at you. 'Are you ready to strike, or will you falter?'",
        "Halsin sighs. 'Nature’s balance must be preserved.'",
        "You hear the eerie whispers of the tadpole in your mind...",
        "Karlach’s infernal engine burns brighter as she laughs joyfully."
    )

    fun getRandomEvent(excludeLogged: Set<String> = emptySet()): String {
        val availableEvents = events.filterNot { it in excludeLogged }
        return if (availableEvents.isNotEmpty()) {
            availableEvents.random()
        } else {
            events.random()
        }
    }
}