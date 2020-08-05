class Test {
    void test(int[] array, int start, int end) {
        int[] test = new int[10];
        <caret>for (// first
                    int i = end - start;
                    // second
                    i < array.length;
                    // third
                    i ++) // fourth
        { // fifth
          test[i - (end - start)] = // sixth
            array[i]; // seventh
        }
    }
}