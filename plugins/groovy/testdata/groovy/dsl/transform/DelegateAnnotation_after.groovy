import java.text.SimpleDateFormat

class Event {
  @Delegate Date when
  String title, url
}

def df = new SimpleDateFormat("yyyy/MM/dd")

def gr8conf = new Event(title: "GR8 Conference",
                        url: "http://www.gr8conf.org",
                        when: df.parse("2009/05/18"))


gr8conf.before(<caret>)