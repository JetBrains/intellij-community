class Comment1 {
  String s = new /*a*//*b*/ <caret>StringBuffer("asdf1").append(/*asdf*/"asdf2").toString(); /* one */ /* two */
}