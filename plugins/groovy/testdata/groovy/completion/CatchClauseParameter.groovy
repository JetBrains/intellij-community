try {
  throw new Exception();
}
catch (e) {
  e.getC<caret>
}