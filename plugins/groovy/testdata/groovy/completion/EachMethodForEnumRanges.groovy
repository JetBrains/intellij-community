enum Nums {
  one, two, tree;
  int foo() {
    return 1;
  }
}
(Nums.one..Nums.tree).each{
  it->
  it.fo<caret>
}
