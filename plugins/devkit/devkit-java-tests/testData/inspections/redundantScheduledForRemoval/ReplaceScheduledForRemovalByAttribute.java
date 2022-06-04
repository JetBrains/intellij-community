import org.jetbrains.annotations.ApiStatus;

<warning descr="@ScheduledForRemoval annotation can be replaced by 'forRemoval' attribute in @Deprecated annotation">@<caret>ApiStatus.ScheduledForRemoval(inVersion = "2022.1")</warning>
@Deprecated
public class ReplaceScheduledForRemovalByAttribute {
}
