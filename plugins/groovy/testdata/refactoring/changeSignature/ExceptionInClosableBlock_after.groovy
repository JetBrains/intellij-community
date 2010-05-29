def foo(String s) throws IOException{}

new Object().each {
  try{
foo("")
}catch(IOException e){
e.printStackTrace()
}
}