public class MultipleDeclarations {
    Integer[] @Required [] values1 = new Integer[1][2];
    Integer[] @Required @Preliminary [] values2 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer[][] values3 = new Integer[5][6];
    Integer[] @Preliminary [] values4;
}