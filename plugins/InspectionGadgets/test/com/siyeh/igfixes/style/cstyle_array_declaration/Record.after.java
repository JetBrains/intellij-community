record R1(int[] x) {
}

record R2(@Required Integer @Required @Preliminary [] @Required @Preliminary [] arr) {
}

record R3(@Required Integer @Required @Preliminary [] @Preliminary [] arr) {
}

record R4(Integer @Required @Preliminary [][] arr) {
}

record R5(Integer[] @Required [][] arr) {
}