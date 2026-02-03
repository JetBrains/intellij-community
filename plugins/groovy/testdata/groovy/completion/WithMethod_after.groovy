File f=new File("abc.txt")
f.with{
  file->
  file.getCanonicalFile()<caret>
}