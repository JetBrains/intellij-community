//region Test configuration
// - hidden: line markers
//endregion
package client;

import kotlinx.datetime.LocalDate;
import sample.DatesKt;
import sample.Event;

public class JavaUsage {
    void test() {
        LocalDate date = DatesKt.firstDayOf2024();
        String formatted = DatesKt.format(date);
        Event event = new Event(date);
        LocalDate eventDate = event.getDate();
    }
}
