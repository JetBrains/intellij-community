def closDelegate = {  String anObject ->
    println anObject
}
def clos = {int i ->
    closDelegate("test")
}