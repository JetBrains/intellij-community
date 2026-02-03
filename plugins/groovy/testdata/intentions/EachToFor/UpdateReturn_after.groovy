Outer:
for (it in [1, 2, 3]) {
    if (it % 2 == 0) {
        while (true) {
            continue Outer
        }
    }

    print 2
}