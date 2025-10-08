[1, 2, 3].e<caret>achWithIndex { int val, int idx ->
    if (val % 2 == 0) {
        while (true) {
            return
        }
    }

    print 2
}