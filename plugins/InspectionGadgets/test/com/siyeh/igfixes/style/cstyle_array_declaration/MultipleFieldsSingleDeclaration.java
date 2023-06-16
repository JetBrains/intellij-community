class X {

    int[] array, array2<caret>[], array3[][];

    int @Preliminary [] arr1 @Required @Preliminary [], arr2 @Preliminary @Required [] = new int[0][2], arr3 @Preliminary @Required [] = new int[0][3]; // same dimensions and annotations

    int @Preliminary [] arr4 @Required @Preliminary [], arr5 @Preliminary @Required [], arr6 @Preliminary []; // same dimensions and different annotations

    int @Preliminary [] arr7 @Required [], arr8 @Preliminary @Required [] = new int[0][8], arr9 @Preliminary @Required []; // same dimensions and different annotations

    int[] arr10 [] = new int[1][0], arr11 [] = new int[1][1], arr12[] = new int[1][2]; // same dimensions

    int @Preliminary [] arr13 [], arr14 @Required [], arr15 @Required []; // different annotations
}