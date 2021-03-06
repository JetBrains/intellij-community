class X {

    int[] array;
    int[][] array2;
    int[][][] array3;

    int @Preliminary [] @Required @Preliminary [] arr1, arr2 = new int[0][2], arr3 = new int[0][3]; // same dimensions and annotations

    int @Preliminary [] @Required @Preliminary [] arr4;
    int @Preliminary [] @Preliminary @Required [] arr5;
    int @Preliminary [] @Preliminary [] arr6; // same dimensions and different annotations

    int @Preliminary [] @Required [] arr7;
    int @Preliminary [] @Preliminary @Required [] arr8 = new int[0][8];
    int @Preliminary [] @Preliminary @Required [] arr9; // same dimensions and different annotations

    int[][] arr10 = new int[1][0], arr11 = new int[1][1], arr12 = new int[1][2]; // same dimensions

    int @Preliminary [][] arr13;
    int @Preliminary [] @Required [] arr14;
    int @Preliminary [] @Required [] arr15; // different annotations
}