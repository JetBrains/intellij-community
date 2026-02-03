Outer:
for (int idx, int val in [1, 2, 3]) {
    if (val % 2 == 0) {
        while (true) {
            continue Outer
        }
    }

    print 2
}