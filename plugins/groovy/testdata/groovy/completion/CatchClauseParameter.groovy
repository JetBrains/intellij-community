try {
  throw new Exception();
}
catch (e) {
  e.getS<caret>
}