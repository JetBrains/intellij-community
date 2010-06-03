def foo(String s) throws IOException{

}

def bar() {
  try{
foo("")
}catch(IOException e){
e.printStackTrace()
}
}

