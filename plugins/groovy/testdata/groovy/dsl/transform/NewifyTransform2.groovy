class Tree {
  Tree(Tree l, Tree r) { }
}

class Leaf extends Tree {
  Leaf(int value) {}
  Leaf(String value) {}
}

@Newify([Tree, Leaf])
buildTree() {
  Tree(Tree(Leaf(1), Leaf(2)), Le<caret>)
}