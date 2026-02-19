[1, 2, 3].eac<caret>hWithIndex { int val, int idx ->
    if (val % 2 == 0) {
        return
    }

    print 2
}