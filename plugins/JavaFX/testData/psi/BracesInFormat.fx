if (type == ENEMY_HOME) {
    println("Invalid level data. Level = {level}, x = {x}, y = {y}");
}

for (i in [0..3]) {
    if (neighbourX < 0) {
        neighbourX = WIDTH - 1
    } else if (neighbourX >= WIDTH) {
        neighbourX = 0
    }
}