def closDelegate = {String anObject ->
    println anObject
}
def clos = {
    closDelegate("smth")
}

clos()