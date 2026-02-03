// PROBLEM: none
// WITH_STDLIB

// Minimized from KT-20714 itself
class ServerUser {
    var id = ""
    var city = ""

    fun toClientUser() = ClientUser().apply {
        id = <caret>this@ServerUser.id
        city = this@ServerUser.city
    }
}

class ClientUser {
    var id = ""
}