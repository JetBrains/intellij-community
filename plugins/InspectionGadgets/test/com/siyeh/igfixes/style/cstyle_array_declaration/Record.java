record R1(int <caret>x[]) {
}

record R2(@Required Integer @Required @Preliminary[] <caret>arr @Required @Preliminary[]) {
}

record R3(@Required Integer <caret>arr    @Required @Preliminary [  ] @Preliminary[]) {
}

record R4(Integer @Required   @Preliminary[] <caret>arr  []) {
}

record R5(Integer [] <caret>arr @Required [] []) {
}