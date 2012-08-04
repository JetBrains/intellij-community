package com.siyeh.igfixes.performance.trivial_string_concatenation;

class Parentheses {

    void foo(int completedTiles, int totalTiles) {
        String s = completedTiles + " , " + (totalTiles - completedTiles);
    }
}