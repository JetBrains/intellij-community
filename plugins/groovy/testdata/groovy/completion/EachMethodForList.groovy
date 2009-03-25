List<String> list=['1', '2', '3']
list.each{
  it->
  it.substr<caret>
}