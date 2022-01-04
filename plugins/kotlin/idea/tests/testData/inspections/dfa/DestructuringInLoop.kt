// WITH_RUNTIME
fun part1(input: List<String>): Int {
    var hp = 0
    var d = 0
    for (command in input) {
        val (op, x) = split(command)
        val xi = x.toInt()
        when (op) {
            "a" -> hp += xi
            "b" -> d += xi
            "c" -> d -= xi
        }
    }
    return hp * d
}

fun part2functional(input: List<String>): Int {
    var horizontalPosition = 0
    var depth = 0
    input
        .map { split(it) }
        .forEach { (direction, amount) ->
            when (direction) {
                "forward" -> horizontalPosition += amount.toInt()
                "up" -> depth -= amount.toInt()
                "down" -> depth += amount.toInt()
            }
        }
    return horizontalPosition * depth
}

fun split(x: String) = arrayOf(x, x)