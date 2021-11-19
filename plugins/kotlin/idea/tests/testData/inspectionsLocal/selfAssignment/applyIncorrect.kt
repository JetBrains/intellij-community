// PROBLEM: Variable 'city' is assigned to itself
// WITH_STDLIB
// FIX: Remove self assignment

// Minimized from KT-20714 itself
class ServerUser {
    var id = ""
    var city = ""

    fun toClientUser() = ClientUser().apply {
        id = this@ServerUser.id
        city = <caret>this@ServerUser.city
    }
}

class ClientUser {
    var id = ""
}